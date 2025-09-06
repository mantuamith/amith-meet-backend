This is a library project for Mult-tenancy functionaliy. It allows the backend application switch do different database schema as per needed.

How to use?

Pre-requisite:  

 1. Add new dabase schema, follow this format "schema_<4 digit tenant ID number>" for the database schema naming convention. 
    Example for tenant Id 2, schema name must be "schema_0002". 
 2. Add the tenant Id to JWT claims attribute named "tenantId" in the JWT bearer token.
 

Steps:

 1. Add this library as dependency in all projects pom.xml.
    Example:
    
    <dependency>
      <groupId>com.algomeet</groupId>
      <artifactId>multi-tenancy-core</artifactId>
      <version>0.0.1-SNAPSHOT</version>
    </dependency>    
 
 2. Make sure that the Autorization token is added every authenticated requests.
    If tenant ID is not set in the JWT token, it will use the "public" schema as default.
 
 3. (This is not applicable to http requests no developers action needed) For websocket communications only, the client must pass the JWT token 
    to the BE server either thru request header or as first message during authentication, then in the BE server will get the tenant Id from 
    the token using JwtHelper class to use this helper class just autowired it in your code.
    
    Example:
    
    @Autowired
    private com.algomeet.multitenancy.util.JwtHelper jwtHelper;
    
    // Use to get the tenant Id from JWT Token
    String tenantId = jwtHelper.getTenantId(token);
    
    
    Then use static method "switchTenantExplicitly(tenantId)" of TenantContext class to switch database schema based on tenant Id.
    // Example code
    com.algomeet.multitenancy.context.TenantContext.switchTenantExplicitly(tenantId);
    
    // More JPA operations
    ...
    
    // then clean-up after the JPA operations.
 	com.algomeet.multitenancy.context.TenantContext.clear();
   
   
    
 
 FAQ:
    
    Q: How to explicitly switch database schema in my code?
    A: You can use the static method "com.algomeet.multitenancy.context.TenantContext.switchTenantExplicitly(tenantId)".
       Make sure you have properly read the documentation within the method comments. It is only advisable 
       to manually switch your database connection for the batch processes, websocket communications and 
       processes with multi-threads. Take note that all http requests were already supported by this library
       no actions from developers needed.
    
    Q: How to switch to public schema while in multi-tenancy session?
    A: You can use the @UsePublicSchema annotation, when you annotate your method with this annotation all
       JPA operations within the method will used the public schema. Make sure to read the annotation documentation
       within the annotation file itself for possible side effects.
       
       Example:
       
       @com.algomeet.multitenancy.annotations.UsePublicSchema
       public myMethod(){
       	 // More JPA operations
       	 ...
       }        
     
 