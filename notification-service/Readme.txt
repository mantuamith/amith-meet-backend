This project allows the backend application to send push notification to both mobile and web applications.


Pre-requisite:  

 Install redis server either in your local machine or in any location which is accessible of your application.


Steps:
   
 1. Make sure that you have installed redis server is up and running.
 
 2. Configure the redis server host and port in application.yml or application.properties of this project.
    
    Below is an example for application.yml configuration
    
	spring:	
	  redis:
	    host: localhost
	    port: 6379

 3. Once everything is properly configured, start the service by running the main class com.algomeet.notificationservice.NotificationServiceApplication 

 4. To test the websocket connection open the sample webclient app "demo-websocket-client.html" in your web browser, the sample web app 
 	located inside resource folder of this project. Make sure that the authorization bearer token is not expired or else generate new token 
 	by re-login using auth-service project. 

    If client cannot connect to the Notification service websocket check the web socket server port number configured in application.yml
    
    Ex.     
    server:
  	  port: 8089
  	  
  	  
  	If client encountered 403 / Forbidden error check that the authorization bearer token is not expired and correct.
  	
  	
To change or update Spring security configurations for the allowed API endpoints and cross domain origin, kindly check the java classes below:
 - com/algomeet/notificationservice/config/SecurityConfig.java 
 - com/algomeet/notificationservice/websocket/config/WebSocketConfig.java
 
 
To change the notification service redis stream key configuration kindly check the configuration class below, be careful on 
changing this value in the application.yml make sure the value is sync with "notification-service-publish-starter" library or 
else the library won't work when used by other projects:
 
 - com/algomeet/notificationservice/properties/RedisStreamConfigProperties.java
 
 
 To push notification from web, mobile app and backend app without direct access to notification service redis-server use the rest API endpoint, 
 you need to passs the authorization bearer token in the request header:
 - /notifications/push
 - /internal/notifications/push
 
 Client app or web app should consume the undelivered user notifications the following API endpoints.
 - /user-notifications/user/{userId}/unread  - User ID must be the same value cab be in users table id field. This value is returned to the client during login.
 - /user-notifications/user/{userId} - To list all notifications either delivered or un-delivered
 - /user-notifications//{id}/read - mark notification as read
 - /user-notifications//{id}/delivered - mark notification as delivered

 