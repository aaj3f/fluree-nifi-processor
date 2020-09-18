/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.fluree.nifi.processors.ingest;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Tags({"DaaS","Bidirectional Netflow"})
@CapabilityDescription("Create a DaaS Artifact")
@SeeAlso({})
@ReadsAttributes({@ReadsAttribute(attribute="", description="")})
@WritesAttributes({@WritesAttribute(attribute="", description="")})
public class MyProcessor extends AbstractProcessor {

    public static final PropertyDescriptor LEDGER_URL_PROPERTY = new PropertyDescriptor
            .Builder().name("Fluree Ledger URL")
            .displayName("Fluree Ledger URL")
            .description("Fluree Ledger URL")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();


    public static final Relationship METADATA_RELATIONSHIP = new Relationship.Builder()
            .name("DaaS Metadata")
            .build();

    public static final Relationship PAYLOAD_RELATIONSHIP = new Relationship.Builder()
            .name("DaaS Payload")
            .build();

    public static final Relationship SUCCEED_INGEST_RELATIONSHIP = new Relationship.Builder()
            .name("Ingest Success")
            .build();                        

    public static final Relationship FAIL_INGEST_RELATIONSHIP = new Relationship.Builder()
            .name("Ingest Fail")
            .build();                                    

    private List<PropertyDescriptor> descriptors;

    private Set<Relationship> relationships;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
        descriptors.add(LEDGER_URL_PROPERTY);
        this.descriptors = Collections.unmodifiableList(descriptors);

        final Set<Relationship> relationships = new HashSet<Relationship>();
        
        relationships.add(METADATA_RELATIONSHIP);
        relationships.add(PAYLOAD_RELATIONSHIP);
        relationships.add(SUCCEED_INGEST_RELATIONSHIP);
        relationships.add(FAIL_INGEST_RELATIONSHIP);

        this.relationships = Collections.unmodifiableSet(relationships);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return this.relationships;
    }

    @Override
    public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return descriptors;
    }

    @OnScheduled
    public void onScheduled(final ProcessContext context) {
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
        FlowFile flowFile = session.get();
        if ( flowFile == null ) {
            return;
        }
        
        try {
            flowFile = session.write(flowFile, (rawIn, rawOut) -> {
                try (final InputStream in = new BufferedInputStream(rawIn); final OutputStream out =
                        new BufferedOutputStream(rawOut)) {

                    // A pre-validate txn would look like this w/ a 200 or 400 response based on valid/invalid txn. Body of 400 would include :status, :message, and :error keys
                    // An actual transaction (insert) would be identical except instead of /gen-flakes in URL it would be /transact
                    // POST, URL=http://localhost:8080/fdb/[NETWORK_NAME]/[LEDGER_NAME]/gen-flakes, BODY=
                    // [
                    //   {
                    //     "_id": "metadata",
                    //     "securityy": {
                    //       "_id": "securityType",
                    //       "Classification": "UNCLASSIFIED",
                    //       "Dissemination": "FOR OFFICIAL USE ONLY"
                    //     },
                    //     "controlSet": {
                    //       "_id": "controlSetType",
                    //       "dataType": "Access Controls",
                    //       "resourceIdentity": {
                    //         "_id": "resourceIdentityType",
                    //         "hash": {
                    //           "_id": "hashType",
                    //           "hash": "bdda4444cbe025bd6ffba8a069b372762818faf02775978066d77b1173c25439",
                    //           "algorithm": "SHA-256"
                    //         },
                    //         "id": "https://www.kaggle.com/heshamasem/binetflow?resource=download/JALvbFxOYwmeddBAmbZW%2Fversions%2F0mAQLG3iPvKhJWRxNMpm%2Ffiles%2Fcapture20110819.binetflow&downloadHash=b914e7ae81618de99514ddab3a4abeb5412bcf66d76eb8542127ad927f0e088e"
                    //       },
                    //       "createDateTime": 1603152000000,
                    //       "dataSource": "acas",
                    //       "responsibleEntity": {
                    //         "_id": "responsibleEntityType",
                    //         "cust": "cust",
                    //         "orig": "orig"
                    //       },
                    //       "resourceDisposition": {
                    //         "_id": "resourceDispositionType",
                    //         "rule": "rule"
                    //       },
                    //       "policyReference": {
                    //         "_id": "policyReferenceType",
                    //         "reference": "ref"
                    //       }
                    //     },
                    //     "provenanceData": {
                    //       "_id": "provenanceType",
                    //       "dataProviderType": "DMSS-KIT",
                    //       "dataProvider": "provider"
                    //     },
                    //     "cdsMetadata": {
                    //       "_id": "cdsMetadataType",
                    //       "destinationDomain": "secret",
                    //       "sourceDomain": "unclassified"
                    //     },
                    //     "ocoMetadata": {
                    //       "_id": "ocoMetadataType",
                    //       "infrastructureUsed": [
                    //         {
                    //           "_id": "infrastructureType",
                    //           "id": "infra1"
                    //         }
                    //       ],
                    //       "cyberToolsUsed": [
                    //         {
                    //           "_id": "cyberToolType",
                    //           "id": "tool1"
                    //         }
                    //       ]
                    //     },
                    //     "authorityReference": {
                    //       "_id": "authorityReferenceType",
                    //       "id": "ref"
                    //     },
                    //     "exercise": "EXERCISE",
                    //     "missionContext": {
                    //       "_id": "missionContext",
                    //       "activity": {
                    //         "_id": "activityType",
                    //         "id": "activity"
                    //       },
                    //       "mission": {
                    //         "_id": "missionType",
                    //         "id": "mission"
                    //       }
                    //     }
                    //   }
                    // ]


                }
            });

        } catch (ProcessException pe) {
            getLogger().error("Failed to deserialize {}", new Object[]{flowFile, pe});
            session.transfer(flowFile, FAIL_INGEST_RELATIONSHIP);
            return;
        }

        flowFile = session.putAttribute(flowFile, CoreAttributes.MIME_TYPE.key(), "application/json");
        session.transfer(flowFile, SUCCEED_INGEST_RELATIONSHIP);
    }

    public static void callingGraph(){
        CloseableHttpClient client= null;
        CloseableHttpResponse response= null;

        client= HttpClients.createDefault();
        HttpPost httpPost= new HttpPost("http://localhost:4000/graphql");

        httpPost.addHeader("Authorization","Bearer myToken");
        httpPost.addHeader("Accept","application/json");

        JSONObject jsonObj = new JSONObject();     
        jsonObj.put("query", "{artifacts{id, metadata {location}} }");

        try {
            StringEntity entity= new StringEntity(jsonObj.toString());

            httpPost.setEntity(entity);
            response= client.execute(httpPost);

        }

        catch(UnsupportedEncodingException e){
            e.printStackTrace();
        }
        catch(ClientProtocolException e){
            e.printStackTrace();
        }
        catch(IOException e){
            e.printStackTrace();
        }

        try{
            BufferedReader reader= new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            String line= null;
            StringBuilder builder= new StringBuilder();
            while((line=reader.readLine())!= null){

                builder.append(line);

            }
            System.out.println(builder.toString());
        }
        catch(Exception e){
            e.printStackTrace();
        }


    }

    
public static void main(String[] args) {
    callingGraph();
    
}

}

