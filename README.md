# project2

## High Level Design
- Centralised Server Architecture with redundancy for backup in case of failure
- All connections (servers and clients alike) shall be established through main centralised server, the only known address of the network
- Main centralised server shall handle the load balancing, registration, and authentication of the entire network
- Clients that have been successfully logged in shall be redirected to a server, where the load across the entire will be evenly distributed
- Servers will handle individual client processes independently thereafter
- To handle possible failure of the centralised server, the network will ensure backup servers to be synchronised with the main central server

### Load Balancing Principles
- To be shared by YJ

### Server to server communication principles
- To be shared by Rahmat

### New Protocols
1. BACKUP_AUTHENTICATE - to authenticate the backup server
  {"command": "BACKUP_AUTHENTICATE",
  "secret": <secret>}
2. BACKUP_AUTHENTICATION_FAIL
  {"command": "BACKUP_AUTHENTICATION_FAIL",
  "info": "the supplied secret is incorrect"}
3. BACKUP_AUTHENTICATION_SUCCESS
  {"command": "BACKUP_AUTHENTICATION_SUCCESS",
  "info": "successfully authenticated with centralised server"}
4. SYNCHRONISE - (former SYNCHRONISE_SERVER_LOAD, SYNCHRONISE_SERVER_ADDRESSES, SYNCHRONISE_USER_STORE merged into 1)
  {"command": "SYNCHRONISE",
  "load": <server client load>,
  "address": <server addresses>,
  "user": <user store>}
5. DE_LOAD 
6. AUTHENTICATE - new information (id, hostname, port)
  {"command": "AUTHENTICATE",
  "id": <server id>,
  "hostname": <server hostname>,
  "port": <port number>}
7. AUTHENTICATE_SUCCESS
  {"command": "AUTHENTICATION_SUCCESS",
  "info": "successfully authenticated with centralised server"}
8. SYNCHRONISED_NEW_SERVER
  {"command": "SYNCHRONISED_NEW_SERVER",
  "id": <server id>,
  "hostname": <server hostname>,
  "port": <port number>}
9. REMOVE_SERVER
  {"command": "REMOVE_SERVER",
  "id": <server id>}

### TODO
- Client CMD Line (Edward)
- Invalid Message (YJ - Done, Eddie - Done)
- Message Communication (Rahmat)
- Update of server load (Eddie - Done)
- Synchronisation of new client update to backup (YJ)
- Synchronisation of when client leaves and update to backup (YJ)
- Synchronisation of new server update to backup (Eddie - Done)
- Synchronisation of when serevr leaves and update to backup (Eddie - Done)
- Synchronisation of registration update to backup (YJ)
- Redirect client to main server when server fails (Edward)
- Failure model of centralised server (Eddie - Done)
  - **how i did it**
  - concept: when main centralised server fails, all clients connected to regular servers will not be affected; redirect all servers to backup
  - hardcopy backup server address to Settings
  - when centralised server is closed, attempt to connect to main centralised server first, if not automatically connect to backup remote hostname and remote port
  - at server end -> send authentication to backup (as per normal) and backup will authenticate server
  - as for the memory, if server id is already found in memory, backup server will not update anything (as compared to authenticating new servers)
- Fix user store (Eddie - Done)
- check if server is authenticated before each synchronise commands (Eddie - Done)
