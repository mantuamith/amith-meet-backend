This is a library project for Mult-tenancy functionaliy. It allows the backend application switch do different database schema as per needed.

How to use?

Pre-requisite:  

 Add new dabase schema, follow this format "schema_<4 digit tenant ID number>" for the database schema naming convention. 
 Example for tenant Id 2, schema name must be "schema_0002".


Steps:

 1. Add this library as dependency in your project pom.xml.
    Example:
    
    <dependency>
      <groupId>com.algomeet</groupId>
      <artifactId>multi-tenancy-core</artifactId>
      <version>0.0.1-SNAPSHOT</version>
    </dependency>    
 
 2. Add the tenant Id in the client request header, tenant ID must be a number between 1 and 9999.
   - X-Tenant-ID 
    
 
 If tenant ID is not set in the request header, it will use the "public" schema as default.
 
   
    