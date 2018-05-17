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

### TODO
- Client CMD Line (Edward)
- Invalid Message (YJ - Done, Eddie)
- Message Communication (Rahmat)
- Update of server load (Eddie - Done)
- Synchronisation of new client update to backup (YJ)
- Synchronisation of when client leaves and update to backup (YJ)
- Synchronisation of new server update to backup (Eddie - Done)
- Synchronisation of when client leaves and update to backup (YJ)
- Synchronisation of registration update to backup (YJ)
- Update centralised server when server/client leaves network and synchronise to backup (Edward)
- Failure model of centralised server (Eddie) 
- Fix user store (Eddie - Done)
- check if server is authenticated before each synchronise commands (Eddie)
