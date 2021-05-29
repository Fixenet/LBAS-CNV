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


import java.io.IOException;

public class amazonExample {
	private static int getActiveMachines(AmazonEC2 ec2){
		int i=0;
		boolean done = false;
		DescribeInstancesRequest describe_request = new DescribeInstancesRequest();
		while(!done) {
    		DescribeInstancesResult response = ec2.describeInstances(describe_request);

    		for(Reservation reservation : response.getReservations()) {
        		for(Instance instance : reservation.getInstances()) {
            		if(instance.getState().getName().equals("running") || instance.getState().getName().equals("pending"))
                		i++;
        		}
    		}

    		describe_request.setNextToken(response.getNextToken());

    		if(response.getNextToken() == null) {
        		done = true;
    		}
		}
		return i;
	}
	private static void deleteAlarm(AmazonCloudWatch cw,String alarm_name){
		DeleteAlarmsRequest request = new DeleteAlarmsRequest()
		.withAlarmNames(alarm_name);

	DeleteAlarmsResult response = cw.deleteAlarms(request);
	}
	private static void createMachine(AmazonEC2 ec2){
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
		System.out.println("Success: "+reservation_id);
		String alarmname=reservation_id+"/CPU-GREATER";
		String alarmname2=reservation_id+"/CPU-LOWER";
		putMetricAlarmCPUGreater(cw,alarmname,reservation_id);
		putMetricAlarmCPULower(cw,alarmname2,reservation_id);
	}
	private static void terminateMachine(AmazonEC2 ec2,String InstanceId){
		final AmazonCloudWatch cw = AmazonCloudWatchClientBuilder.defaultClient();
			System.out.println("Terminating the instance.");
            TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
            termInstanceReq.withInstanceIds(InstanceId);
            ec2.terminateInstances(termInstanceReq);
			deleteAlarm(cw,InstanceId+"/CPU-GREATER");
			deleteAlarm(cw,InstanceId+"/CPU-LOWER");
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
    public static void main(final String[] args) throws IOException {
		final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();

		// CREATING INSTANCE, isto talvez só exista no inicio do nosso sistema
		// depois damos start e stop às instancias conforme necessário 
		// TALVEZ, isto pq n vejo como dar terminate a instances pelo SDK

        RunInstancesRequest run_request = new RunInstancesRequest()
			.withImageId("ami-0484712cbcae24026")
			.withInstanceType(InstanceType.T2Micro)
			.withMinCount(1)
			.withMaxCount(1)
			.withKeyName("CNV-MyKeyPair")
			.withSecurityGroups("CNV-SSH-HTTP");

			createMachine(ec2);
			//terminateMachine(ec2,"i-020fcb4f1b4f92fdf");
		
		/*
        RunInstancesResult run_response = ec2.runInstances(run_request);
        String reservation_id = run_response.getReservation().getInstances().get(0).getInstanceId();
		System.out.println("Success: "+reservation_id);*/

		// STARTING INSTANCE
		/*
		StartInstancesRequest start_request = new StartInstancesRequest()
    		.withInstanceIds("i-0afe583789a8a2ec2");

		ec2.startInstances(start_request);
		*/

		// STOPPING INSTANCE
		/*
		StopInstancesRequest request = new StopInstancesRequest()
    		.withInstanceIds("i-0afe583789a8a2ec2");

		ec2.stopInstances(request);
		*/

		// DESCRIBING INSTANCE, conseguimos o IP por aqui
		boolean done = false;
		DescribeInstancesRequest describe_request = new DescribeInstancesRequest();
		while(!done) {
    		DescribeInstancesResult response = ec2.describeInstances(describe_request);

    		for(Reservation reservation : response.getReservations()) {
        		for(Instance instance : reservation.getInstances()) {
            		System.out.printf(
                		"Found instance with id %s, " +
                		"AMI %s, " +
                		"type %s, " +
						"ip %s, " +
                		"state %s \n",
                		instance.getInstanceId(),
                		instance.getImageId(),
                		instance.getInstanceType(),
						instance.getPublicIpAddress(),
                		instance.getState().getName());
        		}
    		}

    		describe_request.setNextToken(response.getNextToken());

    		if(response.getNextToken() == null) {
        		done = true;
    		}
		}


		//DESCRIBING ALARM, status gives OK or IN ALARM, usamos isso
		final AmazonCloudWatch cw = AmazonCloudWatchClientBuilder.defaultClient();
	while(true){
		try{
		Thread.sleep(5000);
		}catch(InterruptedException e){

		}
		done = false;
		DescribeAlarmsRequest request = new DescribeAlarmsRequest();

		while(!done) {
    		DescribeAlarmsResult response = cw.describeAlarms(request);
			for(MetricAlarm alarm : response.getMetricAlarms()) {
				System.out.printf(
						"Retrieved alarm %s, " +
                		"Status %s\n",
                		alarm.getAlarmName(),
                		alarm.getStateValue());
    		}

    		request.setNextToken(response.getNextToken());

    		if(response.getNextToken() == null) {
        		done = true;
    		}
		}
	}
	}
}
