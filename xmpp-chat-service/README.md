FAT deployment guide:

Deploy xmpp-chat-service to kubernetes/minikube, check sample resources inside "/k8s":
      
   Create the Docker registry secret (if not already)
   This allows Kubernetes to pull images from Harbor:

   kubectl create secret docker-registry harbor-credentials \
     --docker-server=harbor.local \
     --docker-username=admin \
     --docker-password=Harbor12345 \
     --namespace=default


Docker build, push to harbor and deploy, check sample resources inside "/k8s":

   1. Create package: mvn clean package -DskipTests
   2. Remove old image: docker rmi xmpp-chat-service:latest
   3. Docker build: docker build -t xmpp-chat-service:latest .
   4. Create tag:
      docker tag xmpp-chat-service:latest harbor.local/library/xmpp-chat-service:latest
      
   5. Push to harbor
      docker push harbor.local/library/xmpp-chat-service:latest
      
   6. Apply
      kubectl apply -f xmpp-chat-service-deployment.yaml



## 🔌 Connect to the Chat Service

### A. Mobile Clients
Use a WebSocket connection and include the **Authorization header**:
ws://chat.local/ws/chat

**Header:**

Authorization: Bearer <your_token>


---
### B. Web Clients (Browser)
Browsers do not support setting custom headers in WebSocket connections.  
Instead, pass the token as a **query parameter**:

ws://chat.local/ws/chat?token=<your_token>