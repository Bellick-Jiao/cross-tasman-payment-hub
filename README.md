# 🌏 Cross-Tasman ISO 20022 Payment Hub
### Enterprise Hybrid Integration Platform (IBM ACE v13, IBM MQ 9.4 & Spring Boot)
> A cloud-native, event-driven payment integration gateway modernizing cross-border interbank settlement with IBM ACE/MQ, containerized on Docker/K8s.

[![Platform](https://img.shields.io/badge/Platform-IBM%20MQ%209.4%20%7C%20ACE%2013-blue.svg)](https://www.ibm.com/products/app-connect)
[![Spring Boot](https://img.shields.io/badge/Framework-Spring%20Boot%203.x%20%7C%20Spring%20JMS-green.svg)](https://spring.io/projects/spring-boot)
[![Security](https://img.shields.io/badge/Security-OAuth2%20%7C%20JWT%20%7C%20mqsivault-red.svg)](https://jwt.io/)
[![Database](https://img.shields.io/badge/Database-PostgreSQL%2015-orange.svg)](https://www.postgresql.org/)

This repository showcases a **production-ready, bank-grade integration solution** simulating a real-world cross-border payment routing gateway between New Zealand (Auckland) and Australia (Sydney). It demonstrates the seamless modernization of legacy banking integration stacks by marrying traditional **IBM MQ Clustered Messaging & App Connect Enterprise (ACE) v13** with modern **Cloud-Native Containerization, Spring Boot Microservices, OAuth2/JWT security, and Event-Driven Pub-Sub architectures**.

---

## 🗺️ Architectural Topology & Message Flow

![Cross-Tasman Payment Hub Architecture](./diagram1.png)

## 🌟 Technical Highlights & Core Capabilities

### 1. Modern Asynchronous Security: OAuth2 to JWT "Token Pass-Through"
Traditional middleware strips away client security contexts. This architecture solves the **asynchronous distributed security problem**:
*   The Ingress Service intercepts the HTTPS request, verifies the **OAuth2** token, and extracts the signed **JWT**.
*   When sending the message to IBM MQ, the Spring Boot app injects the JWT directly into the **JMS User Properties** (which maps to the binary **`MQRFH2.usr`** header).
*   **IBM ACE v13** utilizes high-performance **ESQL** to extract, cleanse, and restructure the payload into XMLNSC. Crucially, it copies the `MQRFH2.usr` properties sub-tree dynamically while destroying non-standard header fields (such as Java's default `65001` codepage tag), protecting MQ from serialization faults.
*   The **Sydney Receiver** consumes the message, extracts the JWT, and verifies the digital signature **locally (offline)** using public keys, eliminating costly cross-border network overhead.

### 2. Event-Driven Architecture (EDA) & Publish-Subscribe Fan-out
Instead of tight point-to-point queue bindings, the Auckland ACE engine issues events to a logic topic: `Payment/CrossBorder/Initiated`. Within IBM MQ, we configure a **3-way Pub-Sub subscription cluster**:
*   **Core Business Pathway (`SUB_TO_SYDNEY`)**: Listens to the topic and routes messages to an Alias Queue (`PAYMENT.TO.SYDNEY.ALIAS`), which is linked to a Clustered Remote Queue (`PAYMENT.CROSSBORDER.OUT`) bound for Australia.
*   **Compliance Pipeline (`SUB_TO_AML`)**: Intercepts payment records simultaneously, routing them to `PAYMENT.AML.Q` for real-time compliance check by a rule engine.
*   **Audit Pipeline (`SUB_TO_AUDIT`)**: Replicates raw payloads into `PAYMENT.AUDIT.Q` for indexing in ELK (Elasticsearch, Logstash, Kibana) without adding latency to the payment pipeline.

### 3. Active-Active IBM MQ & ACE High-Availability Cluster
*   **MQ Clustered Repository Layout**: Auckland utilizes `QM_AKL_FR` as a Full Repository (Gateway) and `QM_AKL_PR1`/`QM_AKL_PR2` as Partial Repositories. Ingress Spring Boot traffic is dynamically balanced across PR1 and PR2.
*   **Automatic Workload Routing**: If `PR1` fails, traffic is automatically routed to `PR2`. If the remote link to Sydney is disrupted, MQ automatically queues messages on the Auckland Gateway transmission queue (`XMITQ`), guaranteeing **Zero-Data-Loss**.
*   **Poison Message Handling**: Configured with a dedicated Backout Queue (`PAYMENT.CLUSTER.IN.BOQ`) and a threshold of `3`. Problematic messages are gracefully isolated to prevent MQ consumer starvation.

### 4. Zero-Plaintext Secret Management (`mqsivault` & Policies)
Aligned with bank-grade security audits (e.g., PCI-DSS):
*   ACE integration nodes use an encrypted vault (`config/vault`) to store database and MQ credentials.
*   Instead of hardcoding connection strings and passwords inside the BAR file, we implement decoupled **`MQConnection` Policies** (`MqAklPolicy.policyxml`).
*   Passwords are encrypted in local JSON descriptors (`bXEvYWtsTXFBdXRo.json`) and decrypted in-memory inside the Docker container during startup using the `MQSI_VAULT_KEY` environment variable.

---

## 📂 Repository Layout

```text
cross-tasman-payment-hub/
├── README.md                           <-- Document you are reading now
├── auckland/                           <-- Auckland Region (New Zealand)
│   ├── docker-compose.yml              <-- Active-Active Cluster Engine (Postgres, Redis, 3x MQ, 2x ACE)
│   ├── mq-configs/
│   │   ├── akl_fr.mqsc                 <-- Auckland Gateway/Full Repository Config (with Pub-Sub)
│   │   ├── akl_pr1.mqsc                <-- Auckland Node 1 Partial Repository (with Backout Queue)
│   │   └── akl_pr2.mqsc                <-- Auckland Node 2 Partial Repository
│   └── ace-apps/
│       ├── TransformJSONtoISO20022.esql <-- ESQL transforming JSON to SWIFT ISO 20022 and purging 65001 codepage
│       ├── CrossTasmanPolicies/
│       │   └── MqAklPolicy.policyxml    <-- Decoupled MQConnection Policy Project
│       └── ace-vault/
│           └── config/
│               └── vault/               <-- Encrypted Integration Server Vault mapping credentials
└── sydney/                             <-- Sydney Region (Australia)
    ├── docker-compose-syd.yml          <-- Sydney Cluster infrastructure (Postgres, MQ Gateway)
    └── receiver-service/               <-- Sydney Receiver Microservice (Spring Boot 3.x)
        ├── pom.xml
        └── src/main/java/com/cst/
            └── receiver/
                ├── JmsReceiver.java    <-- Listens to pacs.008 XML, extracts JWT, and verifies signature
                └── LedgerService.java  <-- Performs ACID transactions in PostgreSQL database
```

---

## 🛠️ Code Showcases

### Showcase 1: Purging 65001 Codepage & Safely Passing JWT in ESQL

```esql
CREATE COMPUTE MODULE TransformJSONtoISO20022
    CREATE FUNCTION Main() RETURNS BOOLEAN
    BEGIN
        -- =====================================================================
        -- 🌟 STEP 1: PHYSICAL PURGING (Decontaminating the 65001 Codepage Error)
        -- =====================================================================
        -- Do NOT copy 'InputRoot.Properties' as it carries Java's non-standard '65001' (UTF-8)
        -- codepage, which crashes IBM MQ's C-based serialization engine (BIP2132 Error).
        CREATE FIELD OutputRoot.Properties;
        SET OutputRoot.Properties.Domain            = 'XMLNSC';
        SET OutputRoot.Properties.CodedCharSetId    = 1208;       -- Force MQ Standard UTF-8 (1208)
        SET OutputRoot.Properties.Encoding          = 546;        -- Little-Endian
        
        -- Create a fresh MQMD block
        CREATE FIELD OutputRoot.MQMD;
        SET OutputRoot.MQMD.CodedCharSetId = 1208;
        SET OutputRoot.MQMD.Format         = 'MQSTR';             -- Force String Payload
        
        -- Transfer core correlation IDs for async transaction tracking
        SET OutputRoot.MQMD.CorrelId = InputRoot.MQMD.CorrelId;
        SET OutputRoot.MQMD.MsgId    = InputRoot.MQMD.MsgId;
        
        -- =====================================================================
        -- 🌟 STEP 2: JWT PASS-THROUGH VIA RFH2 HEADERS
        -- =====================================================================
        -- Explicitly preserve and copy only the user authentication context sub-tree (JWT)
        IF EXISTS(InputRoot.MQRFH2.usr) THEN
            CREATE FIELD OutputRoot.MQRFH2.usr;
            SET OutputRoot.MQRFH2.usr.Authorization = InputRoot.MQRFH2.usr.Authorization;
        END IF;

        -- =====================================================================
        -- 🌟 STEP 3: CONVERTING JSON TO SWIFT ISO 20022 pacs.008 XML
        -- =====================================================================
        DECLARE ns NAMESPACE 'urn:iso:std:iso:20022:tech:xsd:pacs.008.001.10';
        CREATE FIELD OutputRoot.XMLNSC.ns:Document;
        DECLARE rDoc REFERENCE TO OutputRoot.XMLNSC.ns:Document;
        SET rDoc.(XMLNSC.NamespaceDecl)xmlns = ns;
        
        CREATE FIELD rDoc.ns:FIToFICstmrCdtTrf;
        DECLARE rTrf REFERENCE TO rDoc.ns:FIToFICstmrCdtTrf;
        
        -- Map GrpHdr (Group Header)
        SET rTrf.ns:GrpHdr.ns:MsgId   = InputRoot.JSON.Data.transactionReference;
        SET rTrf.ns:GrpHdr.ns:CreDtTm = CURRENT_TIMESTAMP;
        SET rTrf.ns:GrpHdr.ns:NbOfTxs = 1;
        SET rTrf.ns:GrpHdr.ns:SttlmInf.ns:SttlmMtd = 'CLRG';
        
        -- Map CdtTrfTxInf (Credit Transfer Transaction Info)
        DECLARE rInf REFERENCE TO rTrf.ns:CdtTrfTxInf;
        SET rInf.ns:PmtId.ns:EndToEndId = InputRoot.JSON.Data.transactionReference;
        SET rInf.ns:PmtId.ns:TxId = 'TXN-' || InputRoot.JSON.Data.transactionReference;
        
        -- Double precision banking amount and Currency attribute mapping
        SET rInf.ns:IntrBkSttlmAmt = CAST(InputRoot.JSON.Data.amount AS DECIMAL(18,2));
        SET rInf.ns:IntrBkSttlmAmt.(XMLNSC.Attribute)Ccy = InputRoot.JSON.Data.currency;
        
        -- Map Sender (Debtor)
        SET rInf.ns:Dbtr.ns:Nm = 'Auckland Account Owner';
        SET rInf.ns:DbtrAcct.ns:Id.ns:Othr.ns:Id = InputRoot.JSON.Data.senderAccount;
        
        -- Map Receiver (Creditor)
        SET rInf.ns:Cdtr.ns:Nm = 'Sydney Account Owner';
        SET rInf.ns:CdtrAcct.ns:Id.ns:Othr.ns:Id = InputRoot.JSON.Data.receiverAccount;
        
        -- Map Financial Clearing BIC Binds (Instructing/Instructed Agents)
        SET rInf.ns:InstgAgt.ns:FinInstnId.ns:BICFI = 'AKLNZ2X'; 
        SET rInf.ns:InstdAgt.ns:FinInstnId.ns:BICFI = 'SYDAU2X';
        
        RETURN TRUE;
    END;
END MODULE;
```

### Showcase 2: Clustered Remote Routing & Subscription Definitions (MQSC)

```mqsc
* =============================================================================
* Auckland Gateway / Repository Node (QM_AKL_FR) configuration
* =============================================================================

* 1. Define Cluster & Repository Mapping
ALTER QMGR REPOS(AKL_CLUSTER)

* 2. Define Clustered Receiver Channel (Inbound entry point)
DEFINE CHANNEL(TO.QM_AKL_FR) CHLTYPE(CLUSRCVR) TRPTYPE(TCP) CONNAME('mq-akl-fr(1414)') CLUSTER(AKL_CLUSTER) REPLACE

* 3. Define the Global Pub-Sub Topic
DEFINE TOPIC(PAYMENT.INITIATED.TOPIC) TOPICSTR('Payment/CrossBorder/Initiated') REPLACE

* 4. Define Gateway Alias targeting the Australia Clustered Queue
DEFINE QALIAS(PAYMENT.TO.SYDNEY.ALIAS) TARGET(PAYMENT.CROSSBORDER.OUT) REPLACE

* 5. Bind Subscription to Topic and Route message into the Alias Queue
DEFINE SUB(SUB_TO_SYDNEY) +
       TOPICSTR('Payment/CrossBorder/Initiated') +
       DEST(PAYMENT.TO.SYDNEY.ALIAS) +
       REPLACE

* 6. Bind Subscriptions to local Audit and Compliance queues
DEFINE QLOCAL(PAYMENT.AUDIT.Q) DEFPSIST(YES) REPLACE
DEFINE SUB(SUB_TO_AUDIT) TOPICSTR('Payment/CrossBorder/Initiated') DEST(PAYMENT.AUDIT.Q) REPLACE

DEFINE QLOCAL(PAYMENT.AML.Q) DEFPSIST(YES) REPLACE
DEFINE SUB(SUB_TO_AML) TOPICSTR('Payment/CrossBorder/Initiated') DEST(PAYMENT.AML.Q) REPLACE
```

---

## 🚀 Quick Start / Local Deployment

### Prerequisites
1.  **Docker & Docker Compose** (v2.x+ with WSL2 backend on Windows).
2.  **IBM App Connect Enterprise Developer Edition v13** installation package downloaded locally in your `deps/` folder.

### Step 1: Clone and Build Local ACE 13 Image
To bypass commercial license validation, compile a local developer image using the official `ot4i/ace-docker` scripts:
```bash
git clone https://github.com/ot4i/ace-docker.git
cd ace-docker
# Copy downloaded tarball (e.g., 13.0.x-ACE-LINUX64-DEVELOPER.tar.gz) into the root directory
docker build -t local-ace-server:13.0.1.0 .
```

### Step 2: Spin Up Auckland Region Cluster
Navigate to the `auckland` directory and spin up the multi-container infrastructure:
```bash
cd auckland
docker-compose up -d
```
This launches:
*   `integration-db`: PostgreSQL ledger database.
*   `integration-cache`: Redis cache.
*   `mq-akl-fr`: Auckland Cluster Repository Gateway (Port `1414`).
*   `mq-akl-pr1`: Auckland Cluster Member 1 (Port `1415`).
*   `mq-akl-pr2`: Auckland Cluster Member 2 (Port `1416`).
*   `ace-server-pr1`: Active ACE Engine 1 running decrypted security credentials.
*   `ace-server-pr2`: Active ACE Engine 2.

### Step 3: Verify Container Health and Logs
Check if ACE container automatically unlocked the vault using your environment keys:
```bash
docker logs -f ace-server-pr1
```
*Expected trace:*
```text
BIP1524I: The server vault has been successfully unlocked.
BIP1990I: Integration server 'ACE_PR1' starting initialization; version '13.0.1.0' (64-bit)
BIP9905I: Initializing resource managers.
BIP2155I: The integration server has finished initialization.
```

---

## 🏆 Summary of Enterprise Value
This showcase successfully demonstrates a robust integration framework ready to be discussed at length in architectural review boards:
*   **Decoupled & Highly Scalable**: Fully modular microservices, independent routing networks, and isolated environments.
*   **Resilient against Network Latencies**: Uses self-contained JWT tokens and asynchronous MQ queuing over unstable wide-area networks.
*   **Fully Managed Technical Gaps**: Avoids classical Java-to-MQ codepage mismatch traps and manages state-machine transactions gracefully.
