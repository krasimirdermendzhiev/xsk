package com.sap.xsk.listener;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

public class DelegatingObjectFactory implements ObjectFactory {

  private static final Logger LOGGER = Logger.getLogger(DelegatingObjectFactory.class.getName());

  @Override
  public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) throws NamingException {
    if (obj instanceof Reference) {
      final Reference reference = (Reference) obj;
      final String referenceType = reference.getClassName();
      DelegatingObjectFactory.class.getClassLoader();
      Object objectInstance = getObjectInstanceFromFactory(obj, name, nameCtx, environment, referenceType);
      if (objectInstance != null) {
        return objectInstance;
      }

      objectInstance = getObjectInstanceFromDefaults(obj, name, nameCtx, environment);
      if (objectInstance != null) {
        LOGGER.fine("Object instance is obtained from default factory.");
        return objectInstance;
      }
    }
    throw new NamingException("Object instance with name [" + name + "] could not be created.");
  }

  private Object getObjectInstanceFromFactory(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment,
      final String referenceType) throws NamingException {
    final List<ObjectFactory> factories = getObjectFactories(referenceType);
    for (ObjectFactory factory : factories) {
      Object objectInstance = null;
      try {
        objectInstance = factory.getObjectInstance(obj, name, nameCtx, environment);
      } catch (NameNotFoundException nne) {
        LOGGER.fine("Will move to next factory as facroty [" + factory + "] could not retrieve object instance for name [" + name
            + "]. NameNotFoundException was thrown. " + nne.getMessage());
      } catch (Exception e) {
        LOGGER.severe("Exception is thrown by factory [" + factory + "] during retrieving object instance. Exception is: " + e);
        throw new NamingException("Cannot create resource  object instance due to exception in the object factory");
      }
      if (objectInstance != null) {
        LOGGER.fine("Object instance with name [" + name + "]is created by factory [" + factory + "].");
        return objectInstance;
      }
    }
    return null;
  }

  private Object getObjectInstanceFromDefaults(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) throws NamingException {
    final Reference reference = (Reference) obj;
    RefAddr removedFactory = removeDelegatingObjectFactoryFromReference(reference);
    Object objectInstance = null;

    try {
      String factoryClassName = reference.getFactoryClassName();
      if (factoryClassName != null) {
        ObjectFactory objectFactory = (ObjectFactory) getClass().getClassLoader().loadClass(factoryClassName).newInstance();
        objectInstance = objectFactory.getObjectInstance(reference, name, nameCtx, environment);
      }
    } catch (NameNotFoundException nne) {
      LOGGER.fine("Default object creation factory could not retrieve object instance for name [" + name
          + "]. NameNotFoundException was thrown. " + nne.getMessage());
    } catch (Exception e) {
      LOGGER.severe("Exception is thrown by default object creation facroty during retrieving object instance. Exception is: " + e);
      throw new NamingException("Cannot create resource  object instance due to exception in the object factory");
    } finally {
      if (removedFactory != null) {
        reference.add(removedFactory);
        LOGGER.fine("Add [" + this.getClass().getName() + "] to the communication endpoints of reference object  [" + reference + "].");
      }
    }
    return objectInstance;
  }

  private RefAddr removeDelegatingObjectFactoryFromReference(Reference reference) {
    RefAddr removed = null;
    for (int i = 0; i < reference.size(); i++) {
      final RefAddr refAddr = reference.get(i);
      Object content = refAddr.getContent();
      if (isDelegatedFactoryReference(content)) {
        reference.remove(i);
        removed = refAddr;
        LOGGER.fine("Remove [" + this.getClass().getName() + "] from the communication endpoits of reference object  [" + reference + "].");
        break;
      }
    }
    return removed;
  }

  private boolean isDelegatedFactoryReference(Object content){
    return content != null && this.getClass().getName().equals(content.toString());
  }

  protected List<ObjectFactory> getObjectFactories(final String referenceType) {
    LOGGER.fine(this + " - Searching for object factories... ");

    ClassLoader appClassLoader = Thread.currentThread().getContextClassLoader();
    LOGGER.fine("Context classloader is set, we expect to be app classloader " + appClassLoader);
    List<ObjectFactory> factoriesVisibleFromApp = getObjectFactories(referenceType, appClassLoader);

    List<ObjectFactory> result = new ArrayList<>(factoriesVisibleFromApp);

    LOGGER.fine(this + " - Found object factories visible from app loader " + factoriesVisibleFromApp);
    return result;
  }

  static List<ObjectFactory> getObjectFactories(final String referenceType, ClassLoader classLoader) {
    ServiceLoader<ObjectFactory> serviceLoader = ServiceLoader.load(ObjectFactory.class, classLoader);
    List<ObjectFactory> result = new ArrayList<>();
    try {
      for (ObjectFactory factory : serviceLoader) {
        result.add(factory);
      }
    } catch (ServiceConfigurationError e) {
      LOGGER.log(Level.SEVERE, "Cannot obtain ObjectFactory for reference " + referenceType, e);
    }
    return result;
  }


}