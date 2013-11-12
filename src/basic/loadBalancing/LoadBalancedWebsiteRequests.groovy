package basic.loadBalancing

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.PropertiesCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest
import com.amazonaws.services.ec2.model.DescribeInstancesResult
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.InstanceStateChange
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.LaunchSpecification
import com.amazonaws.services.ec2.model.RequestSpotInstancesRequest
import com.amazonaws.services.ec2.model.RequestSpotInstancesResult
import com.amazonaws.services.ec2.model.Reservation
import com.amazonaws.services.ec2.model.RunInstancesRequest
import com.amazonaws.services.ec2.model.RunInstancesResult
import com.amazonaws.services.ec2.model.SpotInstanceRequest
import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import com.amazonaws.services.ec2.model.TerminateInstancesResult
import com.amazonaws.services.opsworks.model.DescribeInstancesRequest

class LoadBalancedWebsiteRequests {

	private AmazonEC2 ec2
	private List instanceIds


	//Constants
	final private String SECURITY_GROUP = 'StandardWebServerGroup'


	LoadBalancedWebsiteRequests() {
		instanceIds = []
		ec2Init()
	}


	private void ec2Init() throws Exception {

		AWSCredentials credentials = new PropertiesCredentials(Main.getResourceAsStream('/basic/AwsCredentials.properties'))

		ec2 = new AmazonEC2Client(credentials)
		Region usWest2 = Region.getRegion(Regions.US_WEST_2)
		ec2.setRegion(usWest2)
	}



	void submitRequests() {

		//Create security group
		createSecurityGroup()

		//Create EC2 instances and start
		createEC2Instances()

	}

	private createEC2Instances() {
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
		runInstancesRequest.withImageId('ami-aae67f9a')
				.withInstanceType('t1.micro')
				.withMinCount(2)
				.withMaxCount(2)
				.withKeyName('aws-keypair')
				.withSecurityGroups(SECURITY_GROUP)

		RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest)

		//Capture instance ids so we can terminate during clean up
		runInstancesResult.getReservation().getInstances().each {
			println "Instance ID: ${it.getInstanceId()}"
			instanceIds.add(it.getInstanceId())
		}
	}

	private createSecurityGroup() {

		//Check if the security group already exists
		boolean securityGroupExists = false
		DescribeSecurityGroupsResult describeSecurityGroupsResult = ec2.describeSecurityGroups()
		describeSecurityGroupsResult.securityGroups.each {
			if (it.groupName == SECURITY_GROUP) securityGroupExists = true
		}
		if (securityGroupExists) return

		//Create the standard web server security group
		CreateSecurityGroupRequest createSecurityGroupRequest = new CreateSecurityGroupRequest()
		createSecurityGroupRequest.withGroupName(SECURITY_GROUP)
									.withDescription(SECURITY_GROUP)
		ec2.createSecurityGroup(createSecurityGroupRequest)

		//Allow SSH
		IpPermission sshPermission = new IpPermission()
		sshPermission.withIpRanges('0.0.0.0/0')
				.withIpProtocol('tcp')
				.withFromPort(22)
				.withToPort(22)

		//Allow HTTP
		IpPermission httpPermission = new IpPermission()
		httpPermission.withIpRanges('0.0.0.0/0')
						.withIpProtocol('tcp')
						.withFromPort(80)
						.withToPort(80)

		//Open ports
		AuthorizeSecurityGroupIngressRequest authorizeSecurityGroupIngressRequest = new AuthorizeSecurityGroupIngressRequest()
		authorizeSecurityGroupIngressRequest.withGroupName(SECURITY_GROUP)
											.withIpPermissions([sshPermission])
		ec2.authorizeSecurityGroupIngress(authorizeSecurityGroupIngressRequest)

	}

	boolean areAnyOpen() {
		//TODO may want to implement this method to return false if any requests for resources havent completed...
		return false
	}

	public void cleanup () {

		//Terminate instances
		TerminateInstancesRequest terminateInstancesRequest = new TerminateInstancesRequest()
		terminateInstancesRequest.setInstanceIds(instanceIds)
		TerminateInstancesResult terminateInstancesResult = ec2.terminateInstances(terminateInstancesRequest)

		//Wait for instances to terminate
		//http://stackoverflow.com/questions/19883279/terminate-instances-never-returns-terminated-status-aws-sdk

        boolean instancesTerminated = false

        while (instancesTerminated) {
            println 'Waiting for instances to terminate'
            DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest()
            describeInstancesRequest.setInstanceIds(instanceIds)
            DescribeInstancesResult describeInstancesResult = ec2.describeInstances(describeInstancesRequest)


            instancesTerminated = true //assume terminated unless found otherwise
            describeInstancesResult.getInstances().each { instance ->

                println instance.getStatus()

                //is it one of the instances in question?
                instanceIds.each { -> refInstance

                    if ((refInstance == instance.getInstanceId()) && instance.getStatus() != 'terminated') {
                        instancesTerminated = false
                    }
                }
            }
        }

    terminateInstancesResult.getTerminatingInstances().each {

			while (it.getCurrentState().name != "terminated") {
				Thread.sleep(5000)
				println it.getCurrentState().name
				println it.getCurrentState().code
			}

		}
		Thread.sleep(60 * 1000)


		//Delete security group
		DeleteSecurityGroupRequest deleteSecurityGroupRequest = new DeleteSecurityGroupRequest(SECURITY_GROUP)
		ec2.deleteSecurityGroup(deleteSecurityGroupRequest)

		ec2.shutdown()

	}

}
