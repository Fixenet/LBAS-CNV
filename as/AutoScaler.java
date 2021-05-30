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
	static final AmazonCloudWatch cw = AmazonCloudWatchClientBuilder.defaultClient();
	static final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();

	private static Map<String, String> InstanceID_IP = new HashMap<String, String>();
	//MetricStorageSystem
	private static final String MSSserverAddress = "127.0.0.1"; //localhost because they are running on the same machine
	private static final int MSSserverPort = 8001;
	//LoadBalancer
	private static final String LBserverAddress = "127.0.0.1"; //localhost because they are running on the same machine
	private static final int LBserverPort = 8000;

	private static int MAX_AVERAGE = 50000000;

    public static void main(final String[] args) throws IOException {
		updateInstanceID_IP();
		if (getActiveMachines() == 1) createMachine(); //in case only the LB is alive
		updateInstanceID_IP();

		//update the load balancer with systems current state
		for (String instanceID : InstanceID_IP.keySet()) {
			if (instanceID.equals("i-0cb6f8d2b4a139bb6")) continue;
			manageActiveInstances(InstanceID_IP.get(instanceID), "add");
		}

		try {
			while(true) {
				checkAlarms();
				Thread.sleep(60*1000);
			}
		} catch(InterruptedException e) {
			e.printStackTrace();
		}
    }

	private static void checkAlarms() throws IOException {
		int counter = 0;
	
		boolean done = false;
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
						System.out.print(getActiveMachines()+"\n");
						if(getActiveMachines()>2){
							terminateMachine(alarmSplit[0]);
						}
					}

					if(alarmSplit[1].equals("CPU-GREATER")){
						counter++;
					}
				}
			}

			if(counter==getActiveMachines() || getAverageInstructionCount()>=MAX_AVERAGE) {
				createMachine();
			}

			request.setNextToken(response.getNextToken());

			if(response.getNextToken() == null) {
				done = true;
			}
		}
	}
	
	private static int getAverageInstructionCount() throws IOException {
		URL url = new URL("http://"+LBserverAddress+":"+LBserverPort+"/getAverageBlockCount");
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		int status = connection.getResponseCode();
		System.out.println("Status: "+status);

		BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		
		int result = Integer.parseInt(in.readLine());
		in.close();

		System.out.println("Finished reading: AverageBlockCount = "+result);

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
	private static void deleteAlarm(String alarm_name){
		DeleteAlarmsRequest request = new DeleteAlarmsRequest()
			.withAlarmNames(alarm_name);

		DeleteAlarmsResult response = cw.deleteAlarms(request);
	}
	private static void createMachine() throws IOException{
		RunInstancesRequest run_request = new RunInstancesRequest()
			.withImageId("ami-0ac1f10f371d734c6")
			.withInstanceType(InstanceType.T2Micro)
			.withMinCount(1)
			.withMaxCount(1)
			.withKeyName("CNV-MyKeyPair")
			.withSecurityGroups("CNV-SSH-HTTP");
		
		
        RunInstancesResult run_response = ec2.runInstances(run_request);
        String reservation_id = run_response.getReservation().getInstances().get(0).getInstanceId();
		System.out.println("Success creating machine: "+reservation_id);
		String alarmname=reservation_id+"/CPU-GREATER";
		String alarmname2=reservation_id+"/CPU-LOWER";
		String alarmname3=reservation_id+"/NETWORK-IN-LOWER";
		putMetricAlarmCPUGreater(alarmname,reservation_id);
		putMetricAlarmCPULower(alarmname2,reservation_id);
		putMetricAlarmNetworkInLower(alarmname3,reservation_id);

		updateInstanceID_IP();
		manageActiveInstances(InstanceID_IP.get(reservation_id),"add");
	}
	private static void terminateMachine(String InstanceId) throws IOException {
		System.out.println("Terminating instance: "+InstanceId);
        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(InstanceId);
        ec2.terminateInstances(termInstanceReq);
		deleteAlarm(InstanceId+"/CPU-GREATER");
		deleteAlarm(InstanceId+"/CPU-LOWER");
		deleteAlarm(InstanceId+"/NETWORK-IN-LOWER");
			
		manageActiveInstances(InstanceID_IP.get(InstanceId),"remove");
		InstanceID_IP.remove(InstanceId);
	}

	private static void updateInstanceID_IP() {
		boolean done = false;
		boolean containsNULL = false;
		DescribeInstancesRequest describe_request = new DescribeInstancesRequest();
		while(!done || containsNULL) {
			containsNULL = false;
    		DescribeInstancesResult response = ec2.describeInstances(describe_request);

    		for(Reservation reservation : response.getReservations()) {
        		for(Instance instance : reservation.getInstances()) {
					if (!instance.getState().getName().equals("terminated")) {
						InstanceID_IP.put(instance.getInstanceId(), instance.getPublicIpAddress());
						if (instance.getPublicIpAddress() == null) containsNULL = true;
					}
        		}
    		}

    		describe_request.setNextToken(response.getNextToken());

    		if(response.getNextToken() == null) {
        		done = true;
    		}
		}
	}

	private static void putMetricAlarmCPUGreater(String alarmName, String instanceId) {
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
            request.withAlarmDescription("Alarm when server CPU utilization exceeds 80%");
            request.withDimensions(dims);

        cw.putMetricAlarm(request);
        System.out.printf("Successfully created alarm with name %s\n", alarmName);
	}

	private static void putMetricAlarmCPULower(String alarmName, String instanceId) {
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
            request.withAlarmDescription("Alarm when server CPU utilization is lower than 20%");
            request.withDimensions(dims);

        cw.putMetricAlarm(request);
        System.out.printf("Successfully created alarm with name %s\n", alarmName);
	}

	private static void putMetricAlarmNetworkInLower(String alarmName, String instanceId) {
        Dimension instanceDimension = new Dimension();
            instanceDimension.setName("InstanceId");
            List<Dimension> dims = new ArrayList<Dimension>();
			dims.add(instanceDimension);
			instanceDimension.setValue(instanceId);

        PutMetricAlarmRequest request = new PutMetricAlarmRequest();
            request.withAlarmName(alarmName);
            request.withComparisonOperator("LessThanThreshold");
            request.withEvaluationPeriods(1);
            request.withMetricName("NetworkIn");
            request.withNamespace("AWS/EC2");
            request.withPeriod(60);
            request.withStatistic("Average");
            request.withThreshold(20.0);
            request.withActionsEnabled(false);
            request.withAlarmDescription("Alarm when server network input is lower than 20 bytes.");
            request.withDimensions(dims);

        cw.putMetricAlarm(request);
        System.out.printf("Successfully created alarm with name %s\n", alarmName);
	}

}