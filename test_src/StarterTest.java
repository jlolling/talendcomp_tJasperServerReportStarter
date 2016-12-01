
import org.apache.log4j.PropertyConfigurator;

import de.cimt.talendcomp.jasperscheduler.ReportStarter;


public class StarterTest {
	
	public static void main(String[] args) {
		PropertyConfigurator.configureAndWatch(System.getProperty("work.dir") + "/log4j.properties");
		ReportStarter starter = new ReportStarter();
		starter.setStartNow(true);
		starter.setServiceUrl("http://localhost:8080/jasperserver/services/ReportScheduler");
		starter.setServiceUser("jasperadmin");
		starter.setServicePassword("jasperadmin");
		starter.setReportURI("/reports/samples/Department");
		starter.setOutputFileName("Department");
		starter.setOutputFormat("PDF");
		starter.setRepositoryDestURI("/reports/SampleFiles");
		starter.setSequentialFileNames(true);
		starter.setTimestampPattern("yyyyMMdd_HHmmss");
		try {
			starter.setAndConvertParameterCollectionValues("gender","M|F", "|", "String", null);
			starter.setAndConvertParameterCollectionValues("maritalStatus", "S", "|", "String", null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		starter.setParameterValue("department", 1);
		starter.setJobLabel("test2");
		starter.scheduleJob();
		if (starter.getLastException() != null) {
			System.err.println(starter.getLastException());
		}
	}

}
