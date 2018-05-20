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
10. BACKUP_REGISTER - after a client registers in the main centralized server, the transaction is replicated in the backup server
  {"command": "BACKUP_REGISTER".
  "username": <username>,
  "secret": <secret>}
11. BACKUP_INCREASE_LOAD - after a client logins and the server load is updated in the main centralized server, the server load is also updated in the backup server
  {"command":"BACKUP_INCREASE_LOAD",
  "leastId": <server id>,
  "leastLoad": <load count>}
12. BACKUP_DECREASE_LOAD - after a client logouts and the server load is updated in the main centralized server, the server load is also updated in the backup server

### TODO
- Client CMD Line (Edward)
- Invalid Message (YJ - Done, Eddie - Done)
- Message Communication (Rahmat)
- Update of server load (Eddie - Done)
- Synchronisation of new client update to backup (YJ - done)
  - ** how I did it **
  - The centralized server keeps track of the load of every regular server connected to it (in the form of a hash table called serverClientLoad).
  - When a client is redirected to a regular server after a successful login, the serverClientLoad hash table is updated.
  - Every time the serverClientLoad hash table is updated on the centralized server, the transaction is replicated on the backup server so that
  - the contents of its own serverClientLoad hash table will be identical to that of the centralized server's.
- Synchronisation of when client leaves and update to backup (YJ - done)
  - ** how I did it **
  - Same principle as syncing a new client, except that when a client logouts, the serverClientLoad hash table in the centralized server is updated.
  - For every update to the serverClientLoad hash table on the centralized server, the transaction is replicated on the backup server
  - so that the contents of its own serverClientLoad hash table will be identical to that of the centralized server's.
- Synchronisation of new server update to backup (Eddie - Done)
- Synchronisation of when serevr leaves and update to backup (Eddie - Done)
- Synchronisation of registration update to backup (YJ - done)
  - ** how I did it **
  - Concept: A client has to connect to the centralized server (which in turn is connected to a backup server) in order to:
      - register its details first and/or
      - login before being redirected to a normal server.
  - When a client registers on the centralized server, the details are written to the centralized server's database (stored locally).
  - After a successful registration, the same transaction will be replicated on the backup server so that the backup server's database
  - mirrors that of the centralized server's database.

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
