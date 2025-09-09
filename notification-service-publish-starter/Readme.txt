This is a library project for Notification service publish feature. It allows other Java projects to integrate with notification service publish 
notification feature without needing to write complex code and configurations.

How to use?

Pre-requisite:  

 Make sure that the redis server used by the notification sevice is accessible from your machine or the machine you want to run you project.


Steps:

 1. Add this library as dependency in your pom.xml.
    Example:
    
    <dependency>
      <groupId>com.algomeet</groupId>
      <artifactId>notification-service-publish-starter</artifactId>
      <version>0.0.1-SNAPSHOT</version>
    </dependency>
    
 2. Make sure that notification service redis server is up and running.
 
 3. Configure the notification service redis server host and port in application.yml or application.properties of your project.
    
    Below is an example for application.yml configuration
    
	spring:	
	  redis:
	    host: localhost
	    port: 6379

 4. Use autowrired to initialize the NotificationService class in your Java.
    Example:
    
    @Autowired
    private NotificationService notificationService;
    
 5. Use sendPush method of notification service class to push message/notification.
  
    Example:
    
    Notification notif = new Notification();
    
    // To set the receiver you can either initialize the receiverIds or receiverGroup. 
    notif.setReceiverIds(Set.of("username1", "username2"));
    
    // If you use receiver group you should also set the receiverGroupRefId, you can use group 
    // referrence ID to store data such as meeting ID, username, and etc this will be used to lookup/ find 
    // the list of receiver users.
    // To set the receiver group
    notif.setReceiverGroup(ReceiverGroup.USER_FRIENDS);
    // Use to lookup/ find the list of receiver users
    notif.setReceiverGroupRefId("User username")
        
    notif.setType(NotificationType.USER_ONLINE);
    notif.setBody("User is online");
    
    /**
	 * Set the value to true if you want offline users can still retrieve the message once they are back online
	 */
	notif.setDeliveryAckRequired(true or false);
    // To publish
    notificationService.sendPush(notif);    
    
    
 To change the notification service redis stream key configuration kindly check the configuration class below, be careful on 
 changing this value in the application.yml make sure the value is sync with "notification-service" project or else the library 
 won't work when use in your project since notifiation service server is using different stream key.
 
 - com/algomeet/notificationservice/properties/RedisStreamConfigProperties.java
    