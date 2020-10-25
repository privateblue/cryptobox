# cryptobox

## Build and run

After pulling the repository, execute
```sbt run```
to run the application. Or execute 
```sbt test```
to run all the tests.

## API

The application exposes 3-3 endpoints, in 2 different API styles:

#### Create 
Generates a key and returns the corresponding handle.

##### rpc style: 
```POST /rpc/create``` 

Request body: ```{"handle":"<requested handle>"}``` 
(The `handle` field of the request body is optional, and if not set, the server generates a handle.)

Response body: ```{"handle":"<assigned handle>"}```

##### rest style: 
```PUT /rest/handle/<optional requested handle>``` 

Request body: ```{"handle":"<requested handle>"}``` 
(The `handle` field is ignored, as the one in the uri overrides it.)

Response body: ```{"handle":"<assigned handle>"}```

#### Sign 
Signs the provided message with the key corresponding to the provided handle.

##### rpc style: 
```POST /rpc/sign``` 

Request body: ```{"message":"<message to be signed>", "handle":"<handle of key>"}```

Response body: ```{"signature":"<signature of the message>"}```

##### rest style: 
```POST /rest/handle/<handle>/signature``` 

Request body: ```{"message":"<message to be signed>", "handle":"<handle of key>"}```
(The `handle` field is ignored, as the one in the uri overrides it.)

Response body: ```{"signature":"<signature of the message>"}```

#### Verify
Verifies if the provided signature is valid for the provided message and handle.

##### rpc style: 
```POST /rpc/verify``` 

Request body: ```{"message":"<original message>", "signature":"<signature to be verified>", "handle":"<handle of key>"}```

Response body: ```{"verified":"<true/false>"}```

##### rest style: 
```POST /rest/handle/<handle>/signature/verification``` 

Request body: ```{"message":"<original message>", "signature":"<signature to be verified>", "handle":"<handle of key>"}```
(The `handle` field is ignored, as the one in the uri overrides it.)

Response body: ```{"verified":"<true/false>"}```

## ECDSA

The application uses an external java library, [ecdsa-java](https://github.com/starkbank/ecdsa-java), that implements ECDSA, using `secp256k1`. The library has been developed and used by Stark Bank, a digital bank for businesses, and is fairly recent.

## Storage

Generated keys are stored in plain text files, one file per key, in the `data` subdirectory of the project directory. The application uses an in-memory cache to store the keys while running, and saves them to files asynchronously.

## TODO

* Configuration file
* Http request validation
* Logging
