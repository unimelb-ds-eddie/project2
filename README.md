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
3. BACKUP_AUTHENTICATION_SUCCESS
4. SYNCHRONISE_SERVER_LOAD
5. SYNCHRONISE_SERVER_ADDRESSES
6. SYNCHRONISE_USER_STORE
7. DE_LOAD 
8. AUTHENTICATE - new information (id, hostname, port)
9. AUTHENTICATE_SUCCESS
10. SYNCHRONISED_NEW_SERVER
11. REMOVE_SERVER

### TODO
- Client CMD Line (Edward)
- Invalid Message (YJ - Done, Eddie)
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
  - when centralised server is closed, automatically connect to backup remote hostname and remote port
  - at server end -> send authentication to backup (as per normal) and backup will authenticate server
  - as for the memory, if server id is already found in memory, backup server will not update anything (as compared to authenticating new servers)
- Fix user store (Eddie - Done)
- check if server is authenticated before each synchronise commands (Eddie)
