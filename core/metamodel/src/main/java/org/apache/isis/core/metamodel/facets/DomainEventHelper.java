/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.isis.core.metamodel.facets;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.isis.applib.FatalException;
import org.apache.isis.applib.Identifier;
import org.apache.isis.applib.events.domain.AbstractDomainEvent;
import org.apache.isis.applib.events.domain.ActionDomainEvent;
import org.apache.isis.applib.events.domain.CollectionDomainEvent;
import org.apache.isis.applib.events.domain.PropertyDomainEvent;
import org.apache.isis.applib.services.registry.ServiceRegistry;
import org.apache.isis.core.commons.internal.assertions._Assert;
import org.apache.isis.core.commons.internal.collections._Lists;
import org.apache.isis.core.metamodel.facetapi.IdentifiedHolder;
import org.apache.isis.core.metamodel.services.events.MetamodelEventService;
import org.apache.isis.core.metamodel.spec.ManagedObject;
import org.apache.isis.core.metamodel.spec.ObjectSpecification;
import org.apache.isis.core.metamodel.spec.feature.ObjectAction;
import org.apache.isis.core.metamodel.spec.feature.ObjectActionParameter;

import static org.apache.isis.core.commons.internal.base._Casts.uncheckedCast;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;

@RequiredArgsConstructor(staticName = "ofEventService")
public class DomainEventHelper {

    public static DomainEventHelper ofServiceRegistry(final ServiceRegistry serviceRegistry) {
        val eventService = serviceRegistry.lookupServiceElseFail(MetamodelEventService.class);
        return ofEventService(eventService);
    }

    private final MetamodelEventService metamodelEventService;

    // -- postEventForAction
    
    // variant using eventType and no existing event
    public ActionDomainEvent<?> postEventForAction(
            final AbstractDomainEvent.Phase phase,
            @NonNull final Class<? extends ActionDomainEvent<?>> eventType,
            final ObjectAction objectAction,
            final IdentifiedHolder identified,
            final ManagedObject targetAdapter,
            final ManagedObject mixedInAdapter,
            final List<ManagedObject> argumentAdapters,
            final ManagedObject resultAdapter) {
        
        return postEventForAction(phase, uncheckedCast(eventType), /*existingEvent*/null, objectAction, identified, 
                targetAdapter, mixedInAdapter, argumentAdapters, resultAdapter);
    }
    
    // variant using existing event and not eventType (is derived from event)
    public ActionDomainEvent<?> postEventForAction(
            final AbstractDomainEvent.Phase phase,
            @NonNull final ActionDomainEvent<?> existingEvent,
            final ObjectAction objectAction,
            final IdentifiedHolder identified,
            final ManagedObject targetAdapter,
            final ManagedObject mixedInAdapter,
            final List<ManagedObject> argumentAdapters,
            final ManagedObject resultAdapter) {
        
        return postEventForAction(phase, 
                uncheckedCast(existingEvent.getClass()), existingEvent, objectAction, identified, 
                targetAdapter, mixedInAdapter, argumentAdapters, resultAdapter);
    }

    private <S> ActionDomainEvent<S> postEventForAction(
            final AbstractDomainEvent.Phase phase,
            final Class<? extends ActionDomainEvent<S>> eventType,
            final ActionDomainEvent<S> existingEvent,
            final ObjectAction objectAction,
            final IdentifiedHolder identified,
            final ManagedObject targetAdapter,
            final ManagedObject mixedInAdapter,
            final List<ManagedObject> argumentAdapters,
            final ManagedObject resultAdapter) {
        
        _Assert.assertTypeIsInstanceOf(eventType, ActionDomainEvent.class);

        try {
            final ActionDomainEvent<S> event;

            if (existingEvent != null && phase.isExecuted()) {
                // reuse existing event from the executing phase
                event = existingEvent;
            } else {
                // all other phases, create a new event
                final S source = uncheckedCast(ManagedObject.unwrapSingle(targetAdapter));
                final Object[] arguments = ManagedObject.unwrapMultipleAsArray(argumentAdapters);
                final Identifier identifier = identified.getIdentifier();
                event = newActionDomainEvent(eventType, identifier, source, arguments);

                // copy over if have
                if(mixedInAdapter != null ) {
                    event.setMixedIn(mixedInAdapter.getPojo());
                }

                if(objectAction != null) {
                    // should always be the case...
                    event.setActionSemantics(objectAction.getSemantics());

                    val parameters = objectAction.getParameters();

                    val parameterNames = parameters.stream()
                            .map(ObjectActionParameter::getName)
                            .collect(_Lists.toUnmodifiable());

                    final List<Class<?>> parameterTypes = parameters.stream()
                            .map(ObjectActionParameter::getSpecification)
                            .map(ObjectSpecification::getCorrespondingClass)
                            .collect(_Lists.toUnmodifiable());

                    event.setParameterNames(parameterNames);
                    event.setParameterTypes(parameterTypes);
                }
            }

            event.setEventPhase(phase);

            if(phase.isExecuted()) {
                event.setReturnValue(ManagedObject.unwrapSingle(resultAdapter));
            }

            metamodelEventService.fireActionDomainEvent(event);
            return event;
        } catch (Exception e) {
            throw new FatalException(e);
        }
    }

    static <S> ActionDomainEvent<S> newActionDomainEvent(
            final Class<? extends ActionDomainEvent<S>> type,
            final Identifier identifier,
            final S source,
            final Object... arguments) 
        throws InstantiationException, IllegalAccessException, IllegalArgumentException, 
        InvocationTargetException, NoSuchMethodException, SecurityException {
        
        final Constructor<?>[] constructors = type.getConstructors();

        // no-arg constructor
        for (final Constructor<?> constructor : type.getConstructors()) {
            if(constructor.getParameterCount() == 0) {
                final Object event = constructor.newInstance();
                final ActionDomainEvent<S> ade = uncheckedCast(event);
                
                ade.initSource(source);
                ade.setIdentifier(identifier);
                ade.setArguments(asList(arguments));
                return ade;
            }
        }


        for (final Constructor<?> constructor : constructors) {
            final Class<?>[] parameterTypes = constructor.getParameterTypes();
            if(parameterTypes.length != 3) {
                continue;
            }
            if(!parameterTypes[0].isAssignableFrom(source.getClass())) {
                continue;
            }
            if(!parameterTypes[1].isAssignableFrom(Identifier.class)) {
                continue;
            }
            if(!parameterTypes[2].isAssignableFrom(Object[].class)) {
                continue;
            }
            final Object event = constructor.newInstance(source, identifier, arguments);
            return uncheckedCast(event);
        }
        throw new NoSuchMethodException(type.getName()+".<init>(? super " + source.getClass().getName() + ", " + Identifier.class.getName() + ", [Ljava.lang.Object;)");
    }

    // same as in ActionDomainEvent's constructor.
    private static List<Object> asList(final Object[] arguments) {
        return arguments != null
                ? Arrays.asList(arguments)
                        : Collections.emptyList();
    }


    // -- postEventForProperty, newPropertyInteraction
    public <S, T> PropertyDomainEvent<S, T> postEventForProperty(
            final AbstractDomainEvent.Phase phase,
            final Class<? extends PropertyDomainEvent<S, T>> eventType,
                    final PropertyDomainEvent<S, T> existingEvent,
                    final IdentifiedHolder identified,
                    final ManagedObject targetAdapter,
                    final ManagedObject mixedInAdapter,
                    final T oldValue,
                    final T newValue) {
        
        _Assert.assertTypeIsInstanceOf(eventType, PropertyDomainEvent.class);

        try {
            final PropertyDomainEvent<S, T> event;
            final S source = uncheckedCast(ManagedObject.unwrapSingle(targetAdapter));
            final Identifier identifier = identified.getIdentifier();

            if(existingEvent != null && phase.isExecuted()) {
                // reuse existing event from the executing phase
                event = existingEvent;
            } else {
                // all other phases, create a new event
                event = newPropertyDomainEvent(eventType, identifier, source, oldValue, newValue);

                // copy over if have
                if(mixedInAdapter != null ) {
                    event.setMixedIn(mixedInAdapter.getPojo());
                }

            }

            event.setEventPhase(phase);

            // just in case the actual new value held by the object is different from that applied
            setEventNewValue(event, newValue);

            metamodelEventService.firePropertyDomainEvent(event);
            return event;
        } catch (Exception e) {
            throw new FatalException(e);
        }
    }

    private static <S,T> void setEventNewValue(PropertyDomainEvent<S, T> event, T newValue) {
        event.setNewValue(newValue);
    }

    static <S,T> PropertyDomainEvent<S,T> newPropertyDomainEvent(
            final Class<? extends PropertyDomainEvent<S, T>> type,
                    final Identifier identifier,
                    final S source,
                    final T oldValue,
                    final T newValue) throws NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException  {

        final Constructor<?>[] constructors = type.getConstructors();

        // no-arg constructor
        for (final Constructor<?> constructor : constructors) {
            if(constructor.getParameterCount() == 0) {
                final Object event = constructor.newInstance();
                final PropertyDomainEvent<S, T> pde = uncheckedCast(event);
                pde.initSource(source);
                pde.setIdentifier(identifier);
                pde.setOldValue(oldValue);
                pde.setNewValue(newValue);
                return pde;
            }
        }

        // else
        for (final Constructor<?> constructor : constructors) {
            final Class<?>[] parameterTypes = constructor.getParameterTypes();
            if(parameterTypes.length != 4) {
                continue;
            }
            if(!parameterTypes[0].isAssignableFrom(source.getClass())) {
                continue;
            }
            if(!parameterTypes[1].isAssignableFrom(Identifier.class)) {
                continue;
            }
            if(oldValue != null && !parameterTypes[2].isAssignableFrom(oldValue.getClass())) {
                continue;
            }
            if(newValue != null && !parameterTypes[3].isAssignableFrom(newValue.getClass())) {
                continue;
            }
            final Object event = constructor.newInstance(source, identifier, oldValue, newValue);
            return uncheckedCast(event);
        }

        throw new NoSuchMethodException(type.getName()+".<init>(? super " + source.getClass().getName() + ", " + Identifier.class.getName() + ", java.lang.Object, java.lang.Object)");
    }


    // -- postEventForCollection, newCollectionDomainEvent

    public <S, T> CollectionDomainEvent<S, T> postEventForCollection(
            AbstractDomainEvent.Phase phase,
            final Class<? extends CollectionDomainEvent<S, T>> eventType,
                    final CollectionDomainEvent<S, T> existingEvent,
                    final IdentifiedHolder identified,
                    final ManagedObject targetAdapter,
                    final ManagedObject mixedInAdapter,
                    final CollectionDomainEvent.Of of,
                    final T reference) {
        
        _Assert.assertTypeIsInstanceOf(eventType, CollectionDomainEvent.class);
        
        try {
            final CollectionDomainEvent<S, T> event;
            if (existingEvent != null && phase.isExecuted()) {
                // reuse existing event from the executing phase
                event = existingEvent;
            } else {
                // all other phases, create a new event
                final S source = uncheckedCast(ManagedObject.unwrapSingle(targetAdapter));
                final Identifier identifier = identified.getIdentifier();
                event = newCollectionDomainEvent(eventType, phase, identifier, source, of, reference);

                // copy over if have
                if(mixedInAdapter != null ) {
                    event.setMixedIn(mixedInAdapter.getPojo());
                }
            }

            event.setEventPhase(phase);

            metamodelEventService.fireCollectionDomainEvent(event);
            return event;
        } catch (Exception e) {
            throw new FatalException(e);
        }
    }

    <S, T> CollectionDomainEvent<S, T> newCollectionDomainEvent(
            final Class<? extends CollectionDomainEvent<S, T>> type,
                    final AbstractDomainEvent.Phase phase,
                    final Identifier identifier,
                    final S source,
                    final CollectionDomainEvent.Of of,
                    final T value)
                            throws NoSuchMethodException, SecurityException, InstantiationException,
                            IllegalAccessException, IllegalArgumentException, InvocationTargetException {

        final Constructor<?>[] constructors = type.getConstructors();

        // no-arg constructor
        for (final Constructor<?> constructor : constructors) {
            if(constructor.getParameterCount() == 0) {
                final Object event = constructor.newInstance();
                final CollectionDomainEvent<S, T> cde = uncheckedCast(event);

                cde.initSource(source);
                cde.setIdentifier(identifier);
                cde.setOf(of);
                cde.setValue(value);
                return cde;
            }
        }

        // search for constructor accepting source, identifier, type, value
        for (final Constructor<?> constructor : constructors) {
            final Class<?>[] parameterTypes = constructor.getParameterTypes();
            if(parameterTypes.length != 4) {
                continue;
            }
            if(!parameterTypes[0].isAssignableFrom(source.getClass())) {
                continue;
            }
            if(!parameterTypes[1].isAssignableFrom(Identifier.class)) {
                continue;
            }
            if(!parameterTypes[2].isAssignableFrom(CollectionDomainEvent.Of.class)) {
                continue;
            }
            if(value != null && !parameterTypes[3].isAssignableFrom(value.getClass())) {
                continue;
            }
            final Object event = constructor.newInstance(source, identifier, of, value);
            return uncheckedCast(event);
        }

        if(phase == AbstractDomainEvent.Phase.EXECUTED) {
            if(of == CollectionDomainEvent.Of.ADD_TO) {
                // support for @PostsCollectionAddedTo annotation:
                // search for constructor accepting source, identifier, value
                for (final Constructor<?> constructor : constructors) {
                    final Class<?>[] parameterTypes = constructor.getParameterTypes();
                    if(parameterTypes.length != 3) {
                        continue;
                    }
                    if(!parameterTypes[0].isAssignableFrom(source.getClass())) {
                        continue;
                    }
                    if(!parameterTypes[1].isAssignableFrom(Identifier.class)) {
                        continue;
                    }
                    if(value != null && !parameterTypes[2].isAssignableFrom(value.getClass())) {
                        continue;
                    }
                    final Object event = constructor.newInstance(source, identifier, value);
                    return uncheckedCast(event);
                }
            } else if(of == CollectionDomainEvent.Of.REMOVE_FROM) {
                // support for @PostsCollectionRemovedFrom annotation:
                // search for constructor accepting source, identifier, value
                for (final Constructor<?> constructor : constructors) {
                    final Class<?>[] parameterTypes = constructor.getParameterTypes();
                    if(parameterTypes.length != 3) {
                        continue;
                    }
                    if(!parameterTypes[0].isAssignableFrom(source.getClass())) {
                        continue;
                    }
                    if(!parameterTypes[1].isAssignableFrom(Identifier.class)) {
                        continue;
                    }
                    if(value != null && !parameterTypes[2].isAssignableFrom(value.getClass())) {
                        continue;
                    }
                    final Object event = constructor.newInstance(
                            source, identifier, value);
                    return uncheckedCast(event);
                }
            }
        }
        throw new NoSuchMethodException(type.getName()+".<init>(? super " + source.getClass().getName() + ", " + Identifier.class.getName() + ", java.lang.Object)");
    }


}