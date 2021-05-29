package as;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;

import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.InstanceType;

import com.amazonaws.services.ec2.model.StartInstancesRequest;

import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsRequest;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsResult;
import com.amazonaws.services.cloudwatch.model.MetricAlarm;

import com.amazonaws.regions.Region;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest;
import com.amazonaws.services.cloudwatch.model.ComparisonOperator;
import com.amazonaws.services.cloudwatch.model.Statistic;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.amazonaws.services.cloudwatch.model.DeleteAlarmsRequest;
import com.amazonaws.services.cloudwatch.model.DeleteAlarmsResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Date;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import java.util.Map;
import java.util.HashMap;


import java.io.IOException;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.net.URL;
import java.net.HttpURLConnection;

public class AutoScaler {
	private static Map<String, String> InstanceID_IP = new HashMap<String, String>();
	//MetricStorageSystem
	private static final String MSSserverAddress = "127.0.0.1"; //localhost because they are running on the same machine
	private static final int MSSserverPort = 8001;
	//LoadBalancer
	private static final String LBserverAddress = "127.0.0.1"; //localhost because they are running on the same machine
	private static final int LBserverPort = 8000;

	private static int MAX_AVERAGE=50000000;


    public static void main(final String[] args) throws IOException {
		final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
		final AmazonCloudWatch cw = AmazonCloudWatchClientBuilder.defaultClient();
		
		createMachine(ec2);

		int size;
		int counter;
	while(true){
		counter=0;
		try{
		Thread.sleep(60*1000);
		}catch(InterruptedException e){

		}
		Boolean done = false;
		DescribeAlarmsRequest request = new DescribeAlarmsRequest();

		while(!done) {
    		DescribeAlarmsResult response = cw.describeAlarms(request);
			for(MetricAlarm alarm : response.getMetricAlarms()) {
				System.out.printf(
						"Retrieved alarm %s, " +
                		"Status %s\n",
                		alarm.getAlarmName(),
                		alarm.getStateValue());
						if(alarm.getStateValue().equals("ALARM")){
							String[] alarmSplit=alarm.getAlarmName().split("/");
							if(alarmSplit[1].equals("CPU-LOWER")){
								if(getActiveMachines()>1){
									terminateMachine(ec2,alarmSplit[0]);
								}
							}
							if(alarmSplit[1].equals("CPU-GREATER")){
								counter++;
							}
						}
    		}
			if(counter==getActiveMachines()){
				createMachine(ec2);
			}
			if(getAverageInstructionCount()>=MAX_AVERAGE){
				createMachine(ec2);
			}
    		request.setNextToken(response.getNextToken());

    		if(response.getNextToken() == null) {
        		done = true;
    		}
		}
	}
    }
	
	private static int getAverageInstructionCount() throws IOException {
		URL url = new URL("http://"+LBserverAddress+":"+LBserverPort+"/getAverageInstructionCount");
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		int status = connection.getResponseCode();
		System.out.println("Status: "+status);

		BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		
		int result = Integer.parseInt(in.readLine());
		in.close();

		System.out.println("Finished reading: AverageInstructionCount = "+result);

		connection.disconnect();
		System.out.println("Connection closed.");

		return result;
	}

	private static void manageActiveInstances(String instanceIP, String mode) throws IOException {
		URL url = new URL("http://"+LBserverAddress+":"+LBserverPort+"/manageActiveInstances?instanceIP="+instanceIP+"&mode="+mode);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		int status = connection.getResponseCode();
		System.out.println("Status: "+status);

		BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		
		String response = in.readLine();
		in.close();

		System.out.println("Finished reading: "+response);

		connection.disconnect();
		System.out.println("Connection closed.");
	}

	private static int getActiveMachines(){
		return InstanceID_IP.size();
	}
	private static void deleteAlarm(AmazonCloudWatch cw,String alarm_name){
		DeleteAlarmsRequest request = new DeleteAlarmsRequest()
		.withAlarmNames(alarm_name);

	DeleteAlarmsResult response = cw.deleteAlarms(request);
	}
	private static void createMachine(AmazonEC2 ec2) throws IOException{
		final AmazonCloudWatch cw = AmazonCloudWatchClientBuilder.defaultClient();
			RunInstancesRequest run_request = new RunInstancesRequest()
			.withImageId("ami-0ac1f10f371d734c6")
			.withInstanceType(InstanceType.T2Micro)
			.withMinCount(1)
			.withMaxCount(1)
			.withKeyName("CNV-MyKeyPair")
			.withSecurityGroups("CNV-SSH-HTTP");
		
		
        RunInstancesResult run_response = ec2.runInstances(run_request);
        String reservation_id = run_response.getReservation().getInstances().get(0).getInstanceId();
		String reservation_ip = run_response.getReservation().getInstances().get(0).getPublicIpAddress();
		System.out.println("Success: "+reservation_id);
		String alarmname=reservation_id+"/CPU-GREATER";
		String alarmname2=reservation_id+"/CPU-LOWER";
		putMetricAlarmCPUGreater(cw,alarmname,reservation_id);
		putMetricAlarmCPULower(cw,alarmname2,reservation_id);
		InstanceID_IP.put(reservation_id,reservation_ip);
		manageActiveInstances(reservation_ip,"add");
	}
	private static void terminateMachine(AmazonEC2 ec2,String InstanceId)throws IOException{
		final AmazonCloudWatch cw = AmazonCloudWatchClientBuilder.defaultClient();
			System.out.println("Terminating the instance.");
            TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
            termInstanceReq.withInstanceIds(InstanceId);
            ec2.terminateInstances(termInstanceReq);
			deleteAlarm(cw,InstanceId+"/CPU-GREATER");
			deleteAlarm(cw,InstanceId+"/CPU-LOWER");
			InstanceID_IP.remove(InstanceId);
			manageActiveInstances(InstanceID_IP.get(InstanceId),"remove");
	}

	public static void putMetricAlarmCPUGreater(AmazonCloudWatch cw, String alarmName, String instanceId) {

        Dimension instanceDimension = new Dimension();
            instanceDimension.setName("InstanceId");
            List<Dimension> dims = new ArrayList<Dimension>();
			dims.add(instanceDimension);
			instanceDimension.setValue(instanceId);

        PutMetricAlarmRequest request = new PutMetricAlarmRequest();
            request.withAlarmName(alarmName);
            request.withComparisonOperator("GreaterThanThreshold");
            request.withEvaluationPeriods(1);
            request.withMetricName("CPUUtilization");
            request.withNamespace("AWS/EC2");
            request.withPeriod(60);
            request.withStatistic("Average");
            request.withThreshold(80.0);
            request.withActionsEnabled(false);
            request.withAlarmDescription(
                   "Alarm when server CPU utilization exceeds 80%");
            request.withUnit("Seconds");
            request.withDimensions(dims);


        cw.putMetricAlarm(request);
        System.out.printf(
                "Successfully created alarm with name %s\n", alarmName);
	}
	public static void putMetricAlarmCPULower(AmazonCloudWatch cw, String alarmName, String instanceId) {

        Dimension instanceDimension = new Dimension();
            instanceDimension.setName("InstanceId");
            List<Dimension> dims = new ArrayList<Dimension>();
			dims.add(instanceDimension);
			instanceDimension.setValue(instanceId);

        PutMetricAlarmRequest request = new PutMetricAlarmRequest();
            request.withAlarmName(alarmName);
            request.withComparisonOperator("LessThanThreshold");
            request.withEvaluationPeriods(1);
            request.withMetricName("CPUUtilization");
            request.withNamespace("AWS/EC2");
            request.withPeriod(60);
            request.withStatistic("Average");
            request.withThreshold(20.0);
            request.withActionsEnabled(false);
            request.withAlarmDescription(
                   "Alarm when server CPU utilization is lower then 20%");
            request.withUnit("Seconds");
            request.withDimensions(dims);


        cw.putMetricAlarm(request);
        System.out.printf(
                "Successfully created alarm with name %s\n", alarmName);
	}

}