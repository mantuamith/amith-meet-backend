Configure AWS account:


A. Local
  1. Install AWS CLI (one-time)
    macOS
	brew install awscli
	
	Linux
	sudo apt install awscli
	
	Windows
	
	Download from AWS website.
	
	Verify:
	
	aws --version
	
	
	Create / Edit credentials file
  2. Create / Edit credentials file
	Location:
	
	~/.aws/credentials
	
	
	Example:
	
	[default]
	aws_access_key_id = AKIAxxxxxxxxxxxx
	aws_secret_access_key = xxxxxxxxxxxxxxxxxxxxxxxxxx
	
	
	Never commit this file
	
   3. (Recommended) Add config file with region
	
	Location:
	
	~/.aws/config
	
	[default]
	region = ap-southeast-1
	output = json
	
	
	Make sure this matches your S3 bucket region
	
   4. Verify credentials work
	aws sts get-caller-identity
		
	Expected output:
	
	{
	  "UserId": "AIDAXXXXXXXX",
	  "Account": "123456789012",
	  "Arn": "arn:aws:iam::123456789012:user/dev-user"
	}
		
	If this fails → credentials are wrong.

B.(OK for LOCAL DEV): Kubernetes Secret from ~/.aws/credentials

   1. Your local AWS credentials file (Mac)
	
	📍 ~/.aws/credentials
	
	[default]
	aws_access_key_id = AKIAxxxxxxxxxxxx
	aws_secret_access_key = xxxxxxxxxxxxxxxxxxxxxxxxxx
	
	
	⚠️ This is local only. Never commit this.
	
   2. Create a Kubernetes Secret from the credentials file
	kubectl create secret generic aws-credentials \
	  --from-file=credentials=$HOME/.aws/credentials
	
	
	Verify:
	
	kubectl get secret aws-credentials
	
   3. Mount the secret into your Pod
	apiVersion: v1
	kind: Pod
	metadata:
	  name: media-service
	spec:
	  containers:
	    - name: media-service
	      image: algomeet/media-service:dev
	      volumeMounts:
	        - name: aws-credentials
	          mountPath: /root/.aws
	          readOnly: true
	  volumes:
	    - name: aws-credentials
	      secret:
	        secretName: aws-credentials
	
	
	📌 AWS SDK automatically reads:
	
	/root/.aws/credentials
	
	
	No code changes needed.

	

B. Kubernetes Example (EKS IRSA)

   1. Create IAM policy with S3 access:
	
	{
	  "Version": "2012-10-17",
	  "Statement": [
	    {
	      "Effect": "Allow",
	      "Action": [
	        "s3:PutObject",
	        "s3:GetObject",
	        "s3:DeleteObject"
	      ],
	      "Resource": "arn:aws:s3:::your-bucket-name/*"
	    }
	  ]
	}
	
	
   2. Create IAM role for service account:
	
	eksctl create iamserviceaccount \
	  --name mediaservice-sa \
	  --namespace mediaservice \
	  --cluster your-cluster \
	  --attach-policy-arn arn:aws:iam::123456789012:policy/MediaServiceS3Policy \
	  --approve
	
	
   3. Use this Service Account in your pod:
	
	spec:
	  serviceAccountName: mediaservice-sa
	
		
	SDK automatically fetches temporary credentials via the web identity token.



















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


II. Set up AWS credentials MAC local:
	
	1. Create the .aws directory in your home folder:
	
	mkdir -p ~/.aws
	
	2. Create the credentials file
	nano ~/.aws/credentials
	
	
	Paste this, replacing with your IAM user keys:
	
	[default]
	aws_access_key_id = AKIAxxxxxxxxxxxx
	aws_secret_access_key = xxxxxxxxxxxxxxxxxxxxx
	
	
	Save: Ctrl + O → Enter
	
	Exit: Ctrl + X
	
	3. (Optional) Create the config file for your region
	nano ~/.aws/config
	
	
	Paste:
	
	[default]
	region = ap-southeast-1
	output = json
	
	
	Save (Ctrl + O → Enter)
	
	Exit (Ctrl + X)
	
	4.Test the setup
	
	Run:
	
	aws sts get-caller-identity
	
	
	Expected output:
	
	{
	  "UserId": "AIDAxxxxxx",
	  "Account": "850798752380",
	  "Arn": "arn:aws:iam::850798752380:user/media-service-dev"
	}

