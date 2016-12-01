/**
 * Copyright 2015 Jan Lolling jan.lolling@gmail.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cimt.talendcomp.jasperscheduler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;

import com.jaspersoft.ireport.jasperserver.ws.scheduling.ReportSchedulerSoapBindingStub;
import com.jaspersoft.jasperserver.ws.scheduling.IntervalUnit;
import com.jaspersoft.jasperserver.ws.scheduling.Job;
import com.jaspersoft.jasperserver.ws.scheduling.JobMailNotification;
import com.jaspersoft.jasperserver.ws.scheduling.JobParameter;
import com.jaspersoft.jasperserver.ws.scheduling.JobRepositoryDestination;
import com.jaspersoft.jasperserver.ws.scheduling.JobSimpleTrigger;
import com.jaspersoft.jasperserver.ws.scheduling.JobSummary;
import com.jaspersoft.jasperserver.ws.scheduling.ResultSendType;

/**
 * Schedule jobs in JasperReportServer or gather information about jobs.
 * @author jan.lolling@gmail.com
 *
 */
public class ReportStarter {

	private static final Logger logger = Logger.getLogger(ReportStarter.class);
	private final String SERVICE_ADDRESS = "Service.address";
	private final String SERVICE_USER = "Service.user";
	private final String SERVICE_PASSWORD = "Service.password";
	private final String JOB_ID = "Job.id";
	private final String JOB_REPORT_UNIT_URI = "Job.reportUnitUri";
	private final String JOB_USERNAME = "Job.username";
	private final String JOB_LABEL = "Job.label";
	private final String JOB_DESCRIPTION = "Job.description";
	private final String JOB_PARAMETER_PREFIX = "Job.parameter.";
	private final String JOB_BASE_OUTPUT_FILENAME = "Job.baseOutputFilename";
	private final String JOB_OUTPUT_FORMATS = "Job.outputFormats"; 
	private final String JOB_OUTPUT_LOCALE = "Job.outputLocale";
	private final String REPOSITORY_DESTINATION_FOLDER_URI = "JobRepositoryDestination.folderURI";
	private final String REPOSITORY_DESTINATION_OUTPUT_DESC = "JobRepositoryDestination.outputDescription";
	private final String REPOSITORY_DESTINATION_SEQUENTIAL_FILE_NAMES = "JobRepositoryDestination.sequentialFilenames";
	private final String REPOSITORY_DESTINATION_OVERWRITE_FILES = "JobRepositoryDestination.overwriteFiles";
	private final String REPOSITORY_DESTINATION_TIMESTAMP_PATTERN = "JobRepositoryDestination.timestampPattern";
	private final String JOB_MAIL_NOTIFICATION_TO_ADDRESSES = "JobMailNotification.toAddresses";
	private final String JOB_MAIL_NOTIFICATION_SUBJECT = "JobMailNotification.subject";
	private final String JOB_MAIL_NOTIFICATION_MESSAGE = "JobMailNotification.message";
	private final String JOB_MAIL_NOTIFICATION_RESULT_SEND_TYPE = "JobMailNotification.resultSendtype";
	private final String JOB_MAIL_NOTIFICATION_SKIP_EMPTY_RESULTS = "JobMailNotification.skipEmptyResults";
	private final String EMAIL_ADDRESSLIST_FILE = "Email.list.file";
	private final String JOB_SIMPLE_TRIGGER_OCCURRENCE_COUNT = "JobSimpleTrigger.occurrenceCount";
	private final String JOB_SIMPLE_TRIGGER_START_DATE = "JobSimpleTrigger.startDate";
	private final String JOB_SIMPLE_TRIGGER_END_DATE = "JobSimpleTrigger.endDate";
	private final String JOB_SIMPLE_TRIGGER_RECURRENCE_INTERVAL = "JobSimpleTrigger.recurrenceInterval";
	private final String JOB_SIMPLE_TRIGGER_RECURRENCE_UNIT = "JobSimpleTrigger.recurrenceIntervalUnit";
	private Properties configurationProperties = new Properties();
	private boolean useAlternativeParameterMap = false;
	private Map<String, Object> alternativeParameterMap = new HashMap<String, Object>();
	private static final String DEFAULT_DATE_FORMAT = "dd.MM.yyyy HH:mm";
	private static SimpleDateFormat sdf = new SimpleDateFormat(DEFAULT_DATE_FORMAT, Locale.GERMANY);
	private List<String> listEmailAddresses = new ArrayList<String>();
	private Job lastScheduledJob;
	private Exception lastException;
	private boolean printoutLogs = true;
	private boolean startNow = true;
	private int startDelay = 10;
	private String outputLocale = null;
		
	public void setStartNowDelay(Integer seconds) {
		if (seconds != null) {
			startDelay = seconds;
		}
	}
	
	private void setConfigProperty(String key, Object value) {
		if (key == null) {
			throw new IllegalArgumentException("Key cannot be null!");
		}
		if (value == null) {
			logger.debug("remove config property: key=" + key);
			configurationProperties.remove(key);
		} else {
			String s = null;
			if (value instanceof Date) {
				s = sdf.format((Date) value);
			} else if (value instanceof Calendar) {
				s = sdf.format(((Calendar) value).getTime());
			} else if (value instanceof Integer) {
				s = Integer.toString((Integer) value);
			} else {
				s = String.valueOf(value);
			}
			if (logger.isDebugEnabled()) {
				logger.debug("put config property: key=" + key + " value=" + s);
			}
			configurationProperties.put(key, s);
		}
	}
	
	private boolean getBoolean(String key) throws Exception {
		String v = getPropertyOptional(key, "false");
		return Boolean.parseBoolean(v);
	}
	
	private long getLongMandatory(String key) throws Exception {
		String v = getPropertyMandatory(key);
		return Long.parseLong(v);
	}

	private Integer getIntegerOptional(String key, Integer defaultValue) throws Exception {
		String v = getPropertyOptional(key, defaultValue != null ? String.valueOf(defaultValue) : null);
		if (v != null) {
			return Integer.valueOf(v);
		} else {
			return null;
		}
	}

	private String getPropertyMandatory(String key) throws Exception {
		Object o = configurationProperties.get(key);
		if (o != null) {
			String s = (String) o; 
			if (s.trim().isEmpty()) {
				throw new Exception("Config parameter " + key + " cannot be empty");
			}
			return s.trim();
		} else {
			throw new Exception("Config parameter " + key + " cannot be null");
		}
	}
	
	private String getPropertyOptional(String key, String defaultValue) {
		Object o = configurationProperties.get(key);
		if (o != null) {
			String s = (String) o;
			if (s.trim().isEmpty()) {
				return defaultValue;
			} else {
				return s;
			}
		} else {
			return defaultValue;
		}
	}

	private String[] getPropertyArrayMandatory(String key, String delimiters) throws Exception {
		String s = (String) configurationProperties.get(key);
		if (s == null || s.trim().isEmpty()) {
			throw new Exception("Config parameter " + key + " cannot be empty");
		}
		StringTokenizer st = new StringTokenizer(s, delimiters);
		String[] array = new String[st.countTokens()];
		int i = 0;
		while (i < array.length) {
			array[i] = st.nextToken().trim();
			i++;
		}
		return array;
	}

	private String[] getPropertyArrayOptional(String key, String delimiters) {
		String s = (String) configurationProperties.get(key);
		if (s == null || s.trim().isEmpty()) {
			return null;
		}
		StringTokenizer st = new StringTokenizer(s,";");
		String[] array = new String[st.countTokens()];
		int i = 0;
		while (i < array.length) {
			array[i] = st.nextToken().trim();
			i++;
		}
		return array;
	}

	private ReportSchedulerSoapBindingStub createReportScheduler() throws Exception {
		URL url = new URL(getPropertyMandatory(SERVICE_ADDRESS));
		ReportSchedulerSoapBindingStub facade = new ReportSchedulerSoapBindingStub(url, null);
		facade.setUsername(getPropertyMandatory(SERVICE_USER));
		facade.setPassword(getPropertyMandatory(SERVICE_PASSWORD));
		return facade;
	}

	private JobMailNotification createJobMailNotification() throws Exception {
		configureAddressArray();
		if (hasEmailAddresses()) {
			JobMailNotification jmn = new JobMailNotification();
			jmn.setSubject(createSubjectText());
			jmn.setMessageText(createMessageText());
			String resultSendtype = getPropertyOptional(JOB_MAIL_NOTIFICATION_RESULT_SEND_TYPE, null);
			if ("SEND_ATTACHMENT".equals(resultSendtype)) {
				jmn.setResultSendType(ResultSendType.SEND_ATTACHMENT);
			} else {
				jmn.setResultSendType(ResultSendType.SEND);
			}
			jmn.setSkipEmptyReports(getBoolean(JOB_MAIL_NOTIFICATION_SKIP_EMPTY_RESULTS));
			jmn.setToAddresses(getEmailAddresses());
			return jmn;
		} else {
			return null;
		}
	}
	
	private String createSubjectText() throws Exception {
		return replaceParameterPlaceholders(getPropertyMandatory(JOB_MAIL_NOTIFICATION_SUBJECT));
	}
	
	private String createMessageText() {
		return replaceParameterPlaceholders(getPropertyOptional(JOB_MAIL_NOTIFICATION_MESSAGE, ""));
	}
	
	private String replaceParameterPlaceholders(String temp) {
		if (temp == null) {
			return null;
		}
		Set<Entry<Object, Object>> entryset = configurationProperties.entrySet();
		StringReplacer sr = new StringReplacer(temp);
		for (Entry<Object, Object> entry : entryset) {
			String phkey = "{" + ((String) entry.getKey()) + "}";
			String value = entry.getValue() != null ? getParamValueStr((String) entry.getValue()) : "";
			sr.replace(phkey, value);
		}
		return sr.getResultText();
	}
	
	private boolean addEmail(String email) {
		if (email != null) {
			email = email.trim().toLowerCase();
			if (listEmailAddresses.contains(email) == false) {
				listEmailAddresses.add(email);
				return true;
			}
		}
		return false;
	}
	
	private boolean hasEmailAddresses() {
		return listEmailAddresses.size() > 0;
	}
	
	private String[] getEmailAddresses() {
		if (listEmailAddresses.size() > 0) {
			String[] array = new String[listEmailAddresses.size()];
			for (int i = 0; i < listEmailAddresses.size(); i++) {
				array[i] = listEmailAddresses.get(i);
			}
			return array;
		} else {
			return null;
		}
	}
	
	private void configureAddressArray()  throws Exception {
		logger.info("Configure Email address list:");
		String[] addressesFromProperty = getPropertyArrayOptional(JOB_MAIL_NOTIFICATION_TO_ADDRESSES, " ;,");
		if (addressesFromProperty != null) {
			int currentSize = listEmailAddresses.size();
			for (String email : addressesFromProperty) {
				addEmail(email);
			}
			logger.info("  " + (listEmailAddresses.size() - currentSize) + " email addresses loaded from properties");
		}
		loadEmailAddresses();
	}
	
	private JobRepositoryDestination createJobRepositoryDestination() throws Exception {
		JobRepositoryDestination jrd = new JobRepositoryDestination();
		jrd.setFolderURI(getPropertyMandatory(REPOSITORY_DESTINATION_FOLDER_URI));
		jrd.setSequentialFilenames(getBoolean(REPOSITORY_DESTINATION_SEQUENTIAL_FILE_NAMES));
		jrd.setOverwriteFiles(getBoolean(REPOSITORY_DESTINATION_OVERWRITE_FILES));
		jrd.setTimestampPattern(getPropertyOptional(REPOSITORY_DESTINATION_TIMESTAMP_PATTERN, null));
		jrd.setOutputDescription(getPropertyOptional(REPOSITORY_DESTINATION_OUTPUT_DESC, null));
		return jrd;
	}
	
	private Calendar createStartCalendar() throws Exception {
		Calendar c = Calendar.getInstance();
		String s = getPropertyOptional(JOB_SIMPLE_TRIGGER_START_DATE, null);
		if (s != null && s.isEmpty() == false) {
			c.setTime(sdf.parse(s));
		} else {
			c.add(Calendar.SECOND, startDelay);
		}
		return c;
	}
	
	private Calendar createEndCalendar() throws Exception {
		Calendar c = Calendar.getInstance();
		String s = getPropertyOptional(JOB_SIMPLE_TRIGGER_END_DATE, null);
		if (s != null) {
			c.setTime(sdf.parse(s));
		} else {
			c = null;
		}
		return c;
	}

	private JobSimpleTrigger createJobSimpleTrigger() throws Exception {
		JobSimpleTrigger t = new JobSimpleTrigger();
		t.setStartDate(createStartCalendar());
		Calendar endCal = createEndCalendar();
		if (endCal != null) {
			t.setEndDate(endCal);
		}
		if (t.getEndDate() != null) {
			t.setOccurrenceCount(getIntegerOptional(JOB_SIMPLE_TRIGGER_OCCURRENCE_COUNT, -1));
		} else {
			t.setOccurrenceCount(getIntegerOptional(JOB_SIMPLE_TRIGGER_OCCURRENCE_COUNT, 1));
		}
		t.setRecurrenceIntervalUnit(getIntervalUnit(getPropertyOptional(JOB_SIMPLE_TRIGGER_RECURRENCE_UNIT, null)));
		t.setRecurrenceInterval(getIntegerOptional(JOB_SIMPLE_TRIGGER_RECURRENCE_INTERVAL, null));
		return t;
	}
	
	private JobSimpleTrigger createJobStartNowTrigger() throws Exception {
		JobSimpleTrigger t = new JobSimpleTrigger();
		t.setStartDate(createStartCalendar());
		t.setEndDate(createStartCalendar());
		t.setOccurrenceCount(getIntegerOptional(JOB_SIMPLE_TRIGGER_OCCURRENCE_COUNT, 1));
		return t;
	}

	private IntervalUnit getIntervalUnit(String name) {
		if (name == null) {
			return null;
		}
		name = name.trim();
		if ("DAY".equalsIgnoreCase(name)) {
			return IntervalUnit.DAY;
		} else if ("HOUR".equalsIgnoreCase(name)) {
			return IntervalUnit.HOUR;
		} else if ("MINUTE".equalsIgnoreCase(name)) {
			return IntervalUnit.MINUTE;
		} else if ("WEEK".equalsIgnoreCase(name)) {
			return IntervalUnit.WEEK;
		} else {
			logger.error("getIntervalUnit failed: unknown interval: " + name);
			return null;
		}
	}
	
	private void logJobSummary(JobSummary js) {
		if (js != null) {
			logger.info("JobSummary:");
			logger.info("  ID=" + js.getId());
			logger.info("  Report Unit URI=" + js.getReportUnitURI());
			logger.info("  Label=" + js.getLabel());
			logger.info("  State=" + js.getState());
			logger.info("  Previous fire time=" 
					+ (js.getPreviousFireTime() != null && js.getPreviousFireTime().getTime() != null
							? sdf.format(js.getPreviousFireTime().getTime()) 
							: ""));
			logger.info("  Next fire time=" 
					+ (js.getNextFireTime() != null && js.getNextFireTime().getTime() != null
							? sdf.format(js.getNextFireTime().getTime()) 
							: ""));
			logger.info("  Username=" + js.getUsername());
		} else {
			logger.info("JobSummary: not exists");
		}
	}
	
	public void listJobs() {
		logger.info("List All Jobs...");
		try {
			ReportSchedulerSoapBindingStub facade = createReportScheduler();
			JobSummary[] jobSummaries = facade.getAllJobs();
			if (jobSummaries == null) {
				logger.info("There are no Jobs available");
			} else {
				for (JobSummary js : jobSummaries) {
					logger.info("-----------------------------------");
					logJobSummary(js);
					Job job = facade.getJob(js.getId());
					logJob(job);
				}
			}
		} catch (Exception e) {
			logger.error("listJobs failed: " + e.getMessage(), e);
		}
	}
	
	private JobParameter[] createJobParameters() throws Exception {
		List<JobParameter> list = new ArrayList<JobParameter>();
		if (useAlternativeParameterMap) {
			for (Map.Entry<String, Object> entry : alternativeParameterMap.entrySet()) {
				JobParameter param = new JobParameter();
				param.setName(entry.getKey());
				param.setValue(entry.getValue());
				list.add(param);
			}
		} else {
			Set<Object> keys = configurationProperties.keySet();
			Iterator<Object> it = keys.iterator();
			while (it.hasNext()) {
				String key = (String) it.next();
				if (key.startsWith(JOB_PARAMETER_PREFIX)) {
					String paramName = key.substring(JOB_PARAMETER_PREFIX.length());
					String combindValue = configurationProperties.getProperty(key);
					JobParameter param = new JobParameter();
					param.setName(paramName);
					param.setValue(createParamValue(combindValue));
					list.add(param);
				}
			}
		}
		if (list.size() > 0) {
			JobParameter[] array = new JobParameter[list.size()];
			for (int i = 0; i < list.size(); i++) {
				array[i] = list.get(i);
			}
			return array;
		} else {
			return null;
		}
	}
	
	private static String getParamValueStr(String combinedValue) {
		String valueStr = null;
		int pos0 = combinedValue.indexOf('|');
		if (pos0 == -1) {
			valueStr = combinedValue;
		} else {
			int pos1 = combinedValue.indexOf('|', pos0 + 1);
			if (pos1 == -1) {
				pos1 = combinedValue.length();
			}
			valueStr = combinedValue.substring(pos0 + 1, pos1);
		}
		return valueStr;
	}
	
	private static Object createParamValue(String combinedValue) throws Exception {
		if (combinedValue == null) {
			return null;
		}
		combinedValue = combinedValue.trim();
		String type = null;
		String valueStr = null;
		String pattern = null;
		int pos0 = combinedValue.indexOf('|');
		if (pos0 == -1) {
			type = "String";
			valueStr = combinedValue;
		} else {
			type = combinedValue.substring(0, pos0).trim();
			int pos1 = combinedValue.indexOf('|', pos0 + 1);
			if (pos1 == -1) {
				pos1 = combinedValue.length();
			} else if (pos1 < combinedValue.length() - 1) {
				// we have a pattern
				pattern = combinedValue.substring(pos1 + 1);
			}
			valueStr = combinedValue.substring(pos0 + 1, pos1);
		}
		Object value = null;
		if ("String".equalsIgnoreCase(type)) {
			value = valueStr;
		} else if ("Date".equalsIgnoreCase(type) || "Timestamp".equalsIgnoreCase(type)) {
			value = new SimpleDateFormat(pattern).parse(valueStr);
		} else if ("Integer".equalsIgnoreCase(type)) {
			value = Integer.parseInt(valueStr);
		} else if ("Float".equalsIgnoreCase(type)) {
			value = Float.parseFloat(valueStr);
		} else if ("Double".equalsIgnoreCase(type)) {
			value = Double.parseDouble(valueStr);
		} else if ("Boolean".equalsIgnoreCase(type)) {
			value = Boolean.parseBoolean(valueStr);
		} else if ("BigDecimal".equalsIgnoreCase(type)) {
			value = new BigDecimal(valueStr);
		} else {
			throw new Exception("Unknown parameter type: " + type);
		}
		return value;
	}
	
	private Job createJob() throws Exception {
		Job job = new Job();
		job.setId(getIntegerOptional(JOB_ID, 0));
		if (startNow) {
			job.setSimpleTrigger(createJobStartNowTrigger());
		} else {
			job.setSimpleTrigger(createJobSimpleTrigger());
		}
		job.setLabel(getPropertyMandatory(JOB_LABEL));
		job.setReportUnitURI(getPropertyMandatory(JOB_REPORT_UNIT_URI));
		job.setBaseOutputFilename(getPropertyMandatory(JOB_BASE_OUTPUT_FILENAME));
		job.setOutputFormats(getPropertyArrayMandatory(JOB_OUTPUT_FORMATS, ";, "));
		job.setOutputLocale(getPropertyOptional(JOB_OUTPUT_LOCALE, outputLocale));
		job.setUsername(getPropertyOptional(JOB_USERNAME, getPropertyMandatory(SERVICE_USER)));
		job.setDescription(getPropertyOptional(JOB_DESCRIPTION, null));
		job.setRepositoryDestination(createJobRepositoryDestination());
		job.setParameters(createJobParameters());
		// must be the latest to have access to parameters
		job.setMailNotification(createJobMailNotification());
		return job;
	}
	
	public void scheduleJob() {
		logger.info("Schedule Job...");
		ReportSchedulerSoapBindingStub facade;
		try {
			facade = createReportScheduler();
		} catch (Exception e) {
			lastException = e;
			logger.error("scheduleJob: createReportScheduler failed: " + e.getMessage(), e);
			return;
		}
		try {
			Job job = createJob();
			logger.info("JasperReportServer Service URL=" + getPropertyMandatory(SERVICE_ADDRESS));
			logger.info("Send this Job:");
			logJob(job);
			lastScheduledJob = facade.scheduleJob(job);
			if (printoutLogs) {
				logger.info("Job sucessfully scheduled *******************************");
				logger.info("Server respond this Job:");
				logJob(lastScheduledJob);
			}
		} catch (Exception e) {
			lastException = e;
			logger.error("scheduleJob failed: " + e.getMessage(), e);
		}
	}
	
	public void checkJob() {
		logger.info("Check Job...");
		try {
			createReportScheduler(); // only for test of connection
		} catch (Exception e) {
			lastException = e;
			logger.error("scheduleJob: createReportScheduler failed: " + e.getMessage(), e);
			return;
		}
		try {
			Job job = createJob();
			logger.info("JasperReportServer Service URL=" + getPropertyMandatory(SERVICE_ADDRESS));
			logger.info("Check this Job:");
			logJob(job);
		} catch (Exception e) {
			lastException = e;
			logger.error("scheduleJob failed: " + e.getMessage(), e);
		}
	}

	public void listJob() {
		logger.info("List Job...");
		try {
			long jobId = getLongMandatory(JOB_ID);
			listJob(jobId);
		} catch (Exception e) {
			lastException = e;
			logger.error("listJob failed: " + e.getMessage(), e);
		}
	}
	
	public void downloadJob() {
		logger.info("Download Job...");
		try {
			long jobId = getLongMandatory(JOB_ID);
			downloadJob(jobId);
		} catch (Exception e) {
			lastException = e;
			logger.error("downloadJob failed: " + e.getMessage(), e);
		}
	}
	
	public void deleteJob() {
		logger.info("Delete Job...");
		try {
			long jobId = getLongMandatory(JOB_ID);
			deleteJob(jobId);
		} catch (Exception e) {
			lastException = e;
			logger.error("deleteJob failed: " + e.getMessage(), e);
		}
	}

	public void updateJob() {
		logger.info("Update Job...");
		ReportSchedulerSoapBindingStub facade;
		try {
			facade = createReportScheduler();
		} catch (Exception e) {
			lastException = e;
			logger.error("updateJob: createReportScheduler failed: " + e.getMessage(), e);
			return;
		}
		try {
			Job job = createJob();
			logger.info("JasperReportServer Service URL=" + getPropertyMandatory(SERVICE_ADDRESS));
			logger.info("Send this Job:");
			logJob(job);
			Job rJob = facade.updateJob(job);
			logger.info("Job sucessfully scheduled *******************************");
			logger.info("Server respond this Job:");
			logJob(rJob);
		} catch (Exception e) {
			lastException = e;
			logger.error("updateJob failed: " + e.getMessage(), e);
		}
	}

	public void listJob(long jobId) {
		ReportSchedulerSoapBindingStub facade;
		try {
			facade = createReportScheduler();
		} catch (Exception e) {
			lastException = e;
			logger.error("listJob: createReportScheduler failed: " + e.getMessage(), e);
			return;
		}
		try {
			Job job = facade.getJob(jobId);
			logJob(job);
		} catch (Exception e) {
			lastException = e;
			logger.error("listJob jobId=" + jobId + " failed: " + e.getMessage(), e);
		}
	}
	
	public void downloadJob(long jobId) {
		ReportSchedulerSoapBindingStub facade;
		try {
			facade = createReportScheduler();
		} catch (Exception e) {
			lastException = e;
			logger.error("downloadJob: createReportScheduler failed: " + e.getMessage(), e);
			return;
		}
		try {
			Job job = facade.getJob(jobId);
			setConfigProperties(job);
		} catch (Exception e) {
			lastException = e;
			logger.error("downloadJob jobId=" + jobId + " failed: " + e.getMessage(), e);
		}
	}
	
	public void deleteJob(long jobId) {
		ReportSchedulerSoapBindingStub facade;
		try {
			facade = createReportScheduler();
		} catch (Exception e) {
			lastException = e;
			logger.error("deleteJob: createReportScheduler failed: " + e.getMessage(), e);
			return;
		}
		try {
			facade.deleteJob(jobId);
			logger.info("Job id=" + jobId + " deleted");
		} catch (Exception e) {
			lastException = e;
			logger.error("deleteJob jobId=" + jobId + " failed: " + e.getMessage(), e);
		}
	}

	private void logJob(Job job) {
		logger.info("Job:");
		logger.info("  ID=" + job.getId());
		logger.info("  Label=" + job.getLabel());
		logger.info("  Report Unit URI=" + job.getReportUnitURI());
		logger.info("  Output Filename=" + job.getBaseOutputFilename());
		String temp = "";
		for (String f : job.getOutputFormats()) {
			temp = temp + f + " ";
		}
		logger.info("  Output formats=" + temp);
		logger.info("  Output locale=" + job.getOutputLocale());
		logJobRepositoryDestination(job.getRepositoryDestination());
		logJobSimpleTrigger(job.getSimpleTrigger());
		logJobMailNotification(job.getMailNotification());
		logParameters(job.getParameters());
	}
	
	private void setConfigProperties(Job job) {
		logger.info("setConfigProperties Job...");
		configurationProperties.clear();
		setConfigProperty(JOB_ID, job.getId());
		setConfigProperty(JOB_LABEL,job.getLabel());
		setConfigProperty(JOB_REPORT_UNIT_URI, job.getReportUnitURI());
		setConfigProperty(JOB_BASE_OUTPUT_FILENAME, job.getBaseOutputFilename());
		String temp = "";
		for (String f : job.getOutputFormats()) {
			temp = temp + f + " ";
		}
		configurationProperties.put(JOB_OUTPUT_FORMATS, temp);
		setConfigProperties(job.getRepositoryDestination());
		setConfigProperties(job.getSimpleTrigger());
		setConfigProperties(job.getMailNotification());
		setConfigProperties(job.getParameters());
	}

	private void logParameters(JobParameter[] params) {
		if (params == null) {
			logger.info("JobParameters: not set");
		} else {
			for (JobParameter param : params) {
				logParameter(param);
			}
		}
	}
	
	private void setConfigProperties(JobParameter[] params) {
		if (params != null) {
			for (JobParameter param : params) {
				setConfigProperties(param);
			}
		}
	}

	private void logParameter(JobParameter param) {
		logger.info("JobParameter:");
		logger.info("  Name=" + param.getName());
		if (param.getValue() != null) {
			logger.info("  ValueClass=" + param.getValue().getClass());
			logger.info("  Value=" + param.getValue());
		} else {
			logger.info("  Value=not set");
		}
	}
	
	private void setConfigProperties(JobParameter param) {
		logger.info("setConfigProperties JobParameter...");
		setConfigProperty(JOB_PARAMETER_PREFIX + param.getName(), buildCombinedValue(param.getValue()));
	}
	
	private String buildCombinedValue(Object value) {
		if (value == null) {
			return null;
		}
		String combinedValue = null;
		if (value instanceof String) {
			combinedValue = "String|" + (String) value;
		} else if (value instanceof Timestamp) {
			combinedValue = "Timestamp|" + sdf.format((Date) value) + "|" + DEFAULT_DATE_FORMAT;
		} else if (value instanceof Date) {
			combinedValue = "Date|" + sdf.format((Date) value) + "|" + DEFAULT_DATE_FORMAT;
		} else if (value instanceof Integer) {
			combinedValue = "Integer|" + String.valueOf(value);
		} else if (value instanceof Long) {
			combinedValue = "Long|" + String.valueOf(value);
		} else if (value instanceof BigDecimal) {
			combinedValue = "BigDecimal|" + String.valueOf(value);
		} else if (value instanceof Float) {
			combinedValue = "Float|" + String.valueOf(value);
		} else if (value instanceof Double) {
			combinedValue = "Double|" + String.valueOf(value);
		} else if (value instanceof Boolean) {
			combinedValue = "Boolean|" + String.valueOf(value);
		} else {
			combinedValue = "Unknown Type|" + String.valueOf(value);
		}
		return combinedValue;
	}
	
	private void setConfigProperties(JobRepositoryDestination dest) {
		if (dest != null) {
			logger.info("setConfigProperties JobRepositoryDestination...");
			setConfigProperty(REPOSITORY_DESTINATION_FOLDER_URI, dest.getFolderURI());
			setConfigProperty(REPOSITORY_DESTINATION_OVERWRITE_FILES, dest.isOverwriteFiles());
			setConfigProperty(REPOSITORY_DESTINATION_TIMESTAMP_PATTERN, dest.getTimestampPattern());
			setConfigProperty(REPOSITORY_DESTINATION_SEQUENTIAL_FILE_NAMES, dest.isSequentialFilenames());
			setConfigProperty(REPOSITORY_DESTINATION_OUTPUT_DESC, dest.getOutputDescription());
		}
	}
	
	private void logJobRepositoryDestination(JobRepositoryDestination dest) {
		if (dest != null) {
			logger.info("RepositoryDestination:");
			logger.info("  Folder URI=" + dest.getFolderURI());
			logger.info("  Overwrite=" + dest.isOverwriteFiles());
			logger.info("  TimestampPattern=" + (dest.getTimestampPattern() != null ? dest.getTimestampPattern() : ""));
			logger.info("  Sequential Filename creation=" + dest.isSequentialFilenames());
		} else {
			logger.info("RepositoryDestination: not set");
		}
	}
	
	private void logJobSimpleTrigger(JobSimpleTrigger t) {
		if (t != null) {
			logger.info("JobSimpleTrigger:");
			logger.info("  ID=" + t.getId());
			logger.info("  Start date=" + (t.getStartDate() != null ? sdf.format(t.getStartDate().getTime()) : ""));
			logger.info("  End date=" + (t.getEndDate() != null ? sdf.format(t.getEndDate().getTime()) : ""));
			logger.info("  Occurrence Count=" + t.getOccurrenceCount());
			logger.info("  Recurrence Interval=" + (t.getRecurrenceInterval() != null ? t.getRecurrenceInterval() : ""));
			logger.info("  Recurrence Interval Unit=" + (t.getRecurrenceIntervalUnit() != null ? t.getRecurrenceIntervalUnit() : ""));
		} else {
			logger.info("JobSimpleTrigger: not set");
		}
	}
	
	private void setConfigProperties(JobSimpleTrigger t) {
		if (t != null) {
			logger.info("setConfigProperties JobSimpleTrigger...");
			setConfigProperty(JOB_SIMPLE_TRIGGER_START_DATE, t.getStartDate());
			setConfigProperty(JOB_SIMPLE_TRIGGER_END_DATE, t.getEndDate());
			setConfigProperty(JOB_SIMPLE_TRIGGER_OCCURRENCE_COUNT, t.getOccurrenceCount());
			setConfigProperty(JOB_SIMPLE_TRIGGER_RECURRENCE_INTERVAL, t.getRecurrenceInterval());
			setConfigProperty(JOB_SIMPLE_TRIGGER_RECURRENCE_UNIT, t.getRecurrenceIntervalUnit());
		}
	}

	private void logJobMailNotification(JobMailNotification jmn) {
		if (jmn != null) {
			logger.info("JobMailNotification:");
			logger.info("  ID=" + jmn.getId());
			String addresses = "";
			int countAddresses = 0;
			boolean firstLoop = true;
			for (String a : jmn.getToAddresses()) {
				if (firstLoop) {
					firstLoop = false;
				} else {
					addresses = addresses + ",";
				}
				addresses = addresses + a;
				countAddresses++;
			}
			logger.info("  toAddresses=" + addresses);
			logger.info("  Count addresses: " + countAddresses);
			logger.info("  Subject=" + jmn.getSubject());
			logger.info("  MessageText=" + jmn.getMessageText());
			logger.info("  ResultSendType=" + jmn.getResultSendType());
		} else {
			logger.info("JobMailNotification: not set");
		}
	}
	
	private void setConfigProperties(JobMailNotification jmn) {
		if (jmn != null) {
			logger.info("setConfigProperties JobMailNotification...");
			String addresses = "";
			boolean firstLoop = true;
			for (String a : jmn.getToAddresses()) {
				if (firstLoop) {
					firstLoop = false;
				} else {
					addresses = addresses + ",";
				}
				addresses = addresses + a;
			}
			setConfigProperty(JOB_MAIL_NOTIFICATION_TO_ADDRESSES, addresses);
			setConfigProperty(JOB_MAIL_NOTIFICATION_SUBJECT, jmn.getSubject());
			setConfigProperty(JOB_MAIL_NOTIFICATION_MESSAGE, jmn.getMessageText());
			setConfigProperty(JOB_MAIL_NOTIFICATION_RESULT_SEND_TYPE, jmn.getResultSendType());
			setConfigProperty(JOB_MAIL_NOTIFICATION_SKIP_EMPTY_RESULTS, jmn.isSkipEmptyReports());
		}
	}
	
	private void loadEmailAddresses() {
		String filePath = getPropertyOptional(EMAIL_ADDRESSLIST_FILE, null);
		if (filePath != null) {
			File f = new File(filePath);
			if (f.canRead()) {
				try {
					int currentSize = listEmailAddresses.size();
					BufferedReader bin = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
					String line = null;
					while ((line = bin.readLine()) != null) {
						if (line.startsWith("#") == false) {
							// ignore comments
							addEmail(line);
						}
					}
					logger.info("  " + (listEmailAddresses.size() - currentSize) + " email addresses loaded from file " + f.getAbsolutePath());
					bin.close();
				} catch (Exception e) {
					lastException = e;
					logger.error("loadEmailAddresses (reading file) failed: " + e.getMessage(), e);
				}
			} else {
				logger.warn("Given address list file " + filePath + " cannot be read");
			}
		} else {
			logger.info("No address list file given");
		}
	}

	public long getJobId() {
		if (lastScheduledJob != null) {
			return lastScheduledJob.getId();
		} else {
			return 0;
		}
	}
	
	private static final String schedulerUrlPath = "/services/ReportScheduler";
	
	public static String checkSchedulerUrl(String urlStr) {
		if (urlStr == null || urlStr.trim().isEmpty()) {
			throw new IllegalArgumentException("url cannot be null or empty");
		}
		if (urlStr.toLowerCase().trim().endsWith(schedulerUrlPath)) {
			// everything is fine
			return urlStr;
		} else {
			// extract url parts
			try {
				URL url = new URL(urlStr);
				String host = url.getHost();
				String prot = url.getProtocol();
				int port = url.getPort();
				String path = url.getPath();
				if (path.length() > 1) {
					int pos = path.indexOf('/', 1);
					if (pos > 0) {
						path = path.substring(0, pos);
					}
					path = path + schedulerUrlPath;
				} else {
					path = schedulerUrlPath;
				}
				StringBuilder newUrl = new StringBuilder();
				newUrl.append(prot);
				newUrl.append("://");
				newUrl.append(host);
				if (port > 0) {
					newUrl.append(":");
					newUrl.append(port);
				}
				newUrl.append(path);
				System.out.println("Given URL:" + urlStr + " changed to a scheduler URL:" + newUrl.toString());
				return newUrl.toString();
			} catch (MalformedURLException e) {
				throw new IllegalArgumentException("URL: " + urlStr + " is not valied:" + e.getMessage(), e);
			}
		}
	}

	public void setServiceUrl(String url) {
		setConfigProperty(SERVICE_ADDRESS, checkSchedulerUrl(url));
	}
	
	public void setServiceUser(String user) {
		setConfigProperty(SERVICE_USER, user);
	}
	
	public void setServicePassword(String password) {
		setConfigProperty(SERVICE_PASSWORD, password);
	}
	
	public void setReportURI(String reportURI) {
		setConfigProperty(JOB_REPORT_UNIT_URI, reportURI);
	}
	
	public void setOutputFileName(String fileName) {
		setConfigProperty(JOB_BASE_OUTPUT_FILENAME, fileName);
	}
	
	public void setRepositoryDestURI(String repDestURI) {
		setConfigProperty(REPOSITORY_DESTINATION_FOLDER_URI, repDestURI);
	}
	
	public void setRepositoryDestOutputDesc(String descr) {
		setConfigProperty(REPOSITORY_DESTINATION_OUTPUT_DESC, descr);
	}
	
	public void setOutputFormat(String format) {
		setConfigProperty(JOB_OUTPUT_FORMATS, format);
	}
	
	public void setOverwriteFiles(boolean overwrite) {
		setConfigProperty(REPOSITORY_DESTINATION_OVERWRITE_FILES, String.valueOf(overwrite));
	}
	
	public void setSequentialFileNames(boolean seq) {
		setConfigProperty(REPOSITORY_DESTINATION_SEQUENTIAL_FILE_NAMES, String.valueOf(seq));
	}
	
	public void setTimestampPattern(String pattern) {
		setConfigProperty(REPOSITORY_DESTINATION_TIMESTAMP_PATTERN, pattern);
	}
	
	public void setMailAddresses(String addresses) {
		setConfigProperty(JOB_MAIL_NOTIFICATION_TO_ADDRESSES, addresses);
	}
	
	public void setMailSkipIfReportIsEmpty(Boolean skip) {
		setConfigProperty(JOB_MAIL_NOTIFICATION_SKIP_EMPTY_RESULTS, skip);
	}

	public void setMailSubject(String subject) {
		setConfigProperty(JOB_MAIL_NOTIFICATION_SUBJECT, subject);
	}
	
	public void setMailMessage(String message) {
		setConfigProperty(JOB_MAIL_NOTIFICATION_MESSAGE, message);
	}
	
	public void setMailSendAttachments(boolean sendAttachments) {
		if (sendAttachments) {
			setConfigProperty(JOB_MAIL_NOTIFICATION_RESULT_SEND_TYPE, ResultSendType.SEND_ATTACHMENT);
		} else {
			setConfigProperty(JOB_MAIL_NOTIFICATION_RESULT_SEND_TYPE, ResultSendType.SEND);
		}
	}
	
	public void setJobLabel(String label) {
		setConfigProperty(JOB_LABEL, label);
	}
	
	public void setStartNow(boolean now) {
		startNow = now;
	}

	public Exception getLastException() {
		return lastException;
	}
	
	public void setParameterValue(String parameterName, Object value) {
		useAlternativeParameterMap = true;
		alternativeParameterMap.put(parameterName, value);
	}

	public void setParameterValue(String parameterName, String value, String dataType, String pattern) throws Exception {
		useAlternativeParameterMap = true;
		alternativeParameterMap.put(parameterName, Util.convertToDatatype(value, dataType, pattern));
	}
	
	public void addValueToCollectionParameter(String parameterName, Object value) {
		useAlternativeParameterMap = true;
		@SuppressWarnings("unchecked")
		Collection<Object> list = (Collection<Object>) alternativeParameterMap.get(parameterName);
		if (list == null) {
			list = new ArrayList<Object>();
			alternativeParameterMap.put(parameterName, list);
		}
		list.add(value);
	}
	
	public void setAndConvertParameterCollectionValues(String parameterName, String valuesAsString, String delimiter, String dataType, String pattern) throws Exception {
		useAlternativeParameterMap = true;
		alternativeParameterMap.put(parameterName, Util.convertToList(valuesAsString, dataType, delimiter, pattern));
	}

	public void setAndConvertParameterValue(String parameterName, String valueAsString, String dataType, String pattern) throws Exception {
		useAlternativeParameterMap = true;
		alternativeParameterMap.put(parameterName, Util.convertToDatatype(valueAsString, dataType, pattern));
	}

	public Object getParameterValue(String parameterName) {
		return alternativeParameterMap.get(parameterName);
	}

	public String getOutputLocale() {
		return outputLocale;
	}

	public void setOutputLocale(String outputLocale) {
		if (outputLocale != null && outputLocale.trim().isEmpty() == false) {
			this.outputLocale = outputLocale;
		}
	}
	
}
