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

import java.io.IOException;

public class amazonExample {
    public static void main(final String[] args) throws IOException {
		final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();

		// CREATING INSTANCE, isto talvez só exista no inicio do nosso sistema
		// depois damos start e stop às instancias conforme necessário 
		// TALVEZ, isto pq n vejo como dar terminate a instances pelo SDK

        RunInstancesRequest run_request = new RunInstancesRequest()
			.withImageId("ami-0d5eff06f840b45e9")
			.withInstanceType(InstanceType.T2Micro)
			.withMinCount(1)
			.withMaxCount(1)
			.withKeyName("CNV-MyKeyPair")
			.withSecurityGroups("CNV-SSH-HTTP");
		
		
        RunInstancesResult run_response = ec2.runInstances(run_request);
        String reservation_id = run_response.getReservation().getInstances().get(0).getInstanceId();
		System.out.println("Success: "+reservation_id);
		
	//lol
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
                		"state %s ",
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
