Configure AWS account:


Guide:
I. Create an IAM user (minimal permissions)
  A.
	- Go to the AWS Management Console → IAM → Users → Add User
	
	Give the user a name, e.g., media-service-dev
			
	Save Access Key ID and Secret Key (you’ll need them for your local setup).
	
	Add programmatic access to your IAM user

  B.
	- Go to the IAM Console → Users → media-service-dev → Security credentials
	
		Scroll to Access keys section
		
		Click Create access key
	
	- Access key best practices & alternatives  (this is “Local code”)
	
	- Download or copy the Access Key ID and Secret Access Key
	
	- These keys are what your local AWS SDK / Spring Boot app will use.