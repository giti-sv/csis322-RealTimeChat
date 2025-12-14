# Real-Time TCP/UDP Chat Application  
### Java Socket Programming with GUI, XOR Encryption & Message Integrity

---

## Overview

This project implements a **real-time chat system** using **Java socket programming**, supporting both **TCP** (reliable, connection-oriented) and **UDP** (connectionless, unreliable) communication models.

The application demonstrates:
- Fundamental differences between TCP and UDP transport protocols
- End-to-end message confidentiality using application-layer encryption
- Message integrity verification using cryptographic hashing
- GUI-based and console-based client interaction

This project was developed as part of **CSIS322 – Computer Networks**.

---

## System Architecture

The system follows a **client–server architecture** and consists of independent TCP and UDP servers, along with corresponding clients.

- **TCP mode** provides reliable, ordered message delivery.
- **UDP mode** provides faster communication with possible packet loss.
- Encryption and integrity checks are implemented at the **application layer**, independent of the transport protocol.

No external libraries or third-party Java packages are used beyond the standard Java API.

---

## Project Structure

```text
RealTimeChat/
│
├── TCP/
│   ├── TCPServer.java
│   ├── TCPClient.java
│   └── Crypto.java
│
├── UDP/
│   ├── UDPServer.java
│   ├── UDPClient.java
│   └── Crypto.java
│
├── GUI/
│   ├── TCPUDPChatClientGUI.java
│   └── Crypto.java
│
└── README.md
```
---

## File Descriptions

**TCPServer.java**  
Handles TCP client connections, message broadcasting, and command processing.

**TCPClient.java**  
Console-based TCP client used for testing and validation.

**UDPServer.java**  
Receives and broadcasts UDP datagrams without delivery guarantees.

**UDPClient.java**  
Console-based UDP client demonstrating connectionless communication.

**TCPUDPChatClientGUI.java**  
Java Swing GUI client supporting both TCP and UDP modes.

**Crypto.java**  
Provides XOR-based encryption, decryption, and SHA-256 message integrity verification.

---

## Message Broadcasting

The chat system implements **application-layer message broadcasting**.  
When a client sends a message to the server, the server forwards the message to **all other connected clients**.

- In **TCP mode**, broadcast messages are delivered reliably and in order.
- In **UDP mode**, broadcast messages may be lost or arrive out of order.

This behavior was verified using multiple concurrent clients and network traffic analysis.

---

## Encryption Design

- **Algorithm:** XOR cipher  
- **Scope:** Message text only  
- **Key:** Shared static key stored in `Crypto.java`  
- **Purpose:** Demonstrate application-layer encryption concepts  

### Message Format
```text
username | timestamp | encrypted_message | sha256_hash
```
- Hash is computed over the plaintext message  
- Encrypted message is transmitted over the network  
- Decryption occurs only at the endpoints  

---

## Message Integrity Verification

- **Hash Algorithm:** SHA-256  

For each message:
1. A hash is computed over the plaintext message  
2. The hash is transmitted alongside the encrypted payload  
3. The receiver recomputes and verifies the hash  
4. Corrupted messages are detected and flagged  

---

## Commands

| Command | Description |
|------|-------------|
| `/list` | Show online users |
| `/whisper <user> <msg>` | Send a private message |
| `/quit` | Disconnect |

---

## TCP vs UDP Behavior

| Feature | TCP | UDP |
|------|-----|-----|
| Reliability | Guaranteed | Not guaranteed |
| Ordering | Preserved | Not preserved |
| Speed | Moderate | Faster |
| Packet Loss | None | Possible |

Observed behavior during testing matched theoretical expectations.

---

## How to Compile and Run

### TCP

```bash
cd RealTimeChat/TCP
javac *.java
java TCPServer
java TCPClient
```
---

### UDP

```bash
cd RealTimeChat/UDP
javac *.java
java UDPServer
java UDPClient
```
---

### GUI

```bash
cd RealTimeChat/GUI
javac *.java
java TCPUDPChatClientGUI
```
---

## Message History

The GUI client includes a **message history feature**.

- Messages are stored locally in the `./history/` directory  
- History is automatically loaded when the same user reconnects  
- History is separated by username, protocol, and server  
- History can be cleared using the `/clearhistory` command  

---

## Network Analysis

Network traffic was analyzed using **Wireshark** on the loopback interface.

- Encrypted payloads appear as unreadable data  
- Plaintext messages are not visible  
- TCP traffic shows reliable delivery  
- UDP traffic shows occasional packet loss  

---

## Conclusion

This project demonstrates a complete real-time chat system using both TCP and UDP transport protocols. It highlights protocol trade-offs, implements encryption and integrity checks, and provides a user-friendly GUI.



