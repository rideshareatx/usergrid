/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.usergrid.persistence.collection.mvcc.stage.load;


import java.util.Iterator;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.usergrid.persistence.collection.CollectionScope;
import org.apache.usergrid.persistence.collection.mvcc.MvccEntitySerializationStrategy;
import org.apache.usergrid.persistence.collection.mvcc.entity.MvccEntity;
import org.apache.usergrid.persistence.collection.mvcc.stage.CollectionIoEvent;
import org.apache.usergrid.persistence.collection.serialization.EntityRepair;
import org.apache.usergrid.persistence.collection.serialization.SerializationFig;
import org.apache.usergrid.persistence.collection.serialization.impl.EntityRepairImpl;
import org.apache.usergrid.persistence.collection.service.UUIDService;
import org.apache.usergrid.persistence.core.util.ValidationUtils;
import org.apache.usergrid.persistence.model.entity.Entity;
import org.apache.usergrid.persistence.model.entity.Id;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import rx.functions.Func1;


/**
 * This stage is a load stage to load a single entity
 */
@Singleton
public class Load implements Func1<CollectionIoEvent<Id>, Entity> {


    private static final Logger LOG = LoggerFactory.getLogger( Load.class );

    private final UUIDService uuidService;
    private final MvccEntitySerializationStrategy entitySerializationStrategy;
    private final EntityRepair entityRepair;


    @Inject
    public Load( final UUIDService uuidService, final MvccEntitySerializationStrategy entitySerializationStrategy, final
                 SerializationFig serializationFig ) {
        Preconditions.checkNotNull( entitySerializationStrategy, "entitySerializationStrategy is required" );
        Preconditions.checkNotNull( uuidService, "uuidService is required" );


        this.uuidService = uuidService;
        this.entitySerializationStrategy = entitySerializationStrategy;
        entityRepair = new EntityRepairImpl( entitySerializationStrategy, serializationFig );

    }

    //TODO: do reads partial merges in batches. maybe 5 or 10 at a time.
    /**
     * for example
     so if like v1 is a full
     and you have v1 -> v20, where v2->20 is all partial
     you merge up to 10, then flush
     then process 10->20, then flush
     */
    @Override
    public Entity call( final CollectionIoEvent<Id> idIoEvent ) {
        final Id entityId = idIoEvent.getEvent();

        ValidationUtils.verifyIdentity( entityId );


        final CollectionScope collectionScope = idIoEvent.getEntityCollection();

        //generate  a version that represents now
        final UUID versionMax = uuidService.newTimeUUID();

        Iterator<MvccEntity> results = entitySerializationStrategy.load(
                collectionScope, entityId, versionMax, 1 );

        if(!results.hasNext()){
            return null;
        }

        final MvccEntity returned = results.next();

        final MvccEntity repairedEntity = entityRepair.maybeRepair( collectionScope,  returned );

        if(repairedEntity == null)
            return null;

        return repairedEntity.getEntity().orNull();

    }
}
