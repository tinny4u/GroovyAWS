package basic.loadBalancing

import com.amazonaws.AmazonServiceException

class Main {
	
	private final static ACTIVE_MINUTES = 1

	static void main (String[] args) {

		println '==========================================='
		println 'Creating a load balanced website!'
		println '==========================================='


		LoadBalancedWebsiteRequests requests
		try {
			// Setup the helper object that will perform all of the API calls.
			requests = new LoadBalancedWebsiteRequests()

			// Submit all of the requests.
			requests.submitRequests()

			//Wait for active period
			Thread.sleep(ACTIVE_MINUTES * 1000 * 60)

			// Cancel all requests and terminate all running instances.
			requests.cleanup()

		} catch (AmazonServiceException ase) {
			// Write out any exceptions that may have occurred.
			println "Caught Exception: ${ase.getMessage()}"
			println "Reponse Status Code: ${ase.getStatusCode()}"
			println "Error Code: ${ase.getErrorCode()}"
			println "Request ID: ${ase.getRequestId()}"
						
			requests.cleanup()
		}
		
	}

}
