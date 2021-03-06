/*
 * Copyright © 2012 jbundle.org. All rights reserved.
 */
package org.jbundle.util.osgi.bundle;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;

import org.jbundle.util.osgi.BundleService;
import org.jbundle.util.osgi.ClassService;
import org.jbundle.util.osgi.finder.BaseClassFinderService;
import org.jbundle.util.osgi.finder.ClassFinderActivator;
import org.jbundle.util.osgi.finder.ClassServiceUtility;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;


/**
 * Bundle resource access utilities for a service.
 * Extend this class with your service activator.
 * @author don
 *
 */
public class BaseBundleService extends Object
	implements BundleService, BundleActivator, ServiceListener
{
	/**
	 * This is not necessary. It is nice for debugging.
	 */
	protected Dictionary<String,String> properties = null;
	
    ServiceRegistration serviceRegistration = null;
    
    protected BundleContext context = null;
    
    /**
     * Bundle starting up.
     */
    public void start(BundleContext context) throws Exception {
        ClassServiceUtility.log(context, LogService.LOG_INFO, "Starting a BaseBundleService bundle: " + this.getClass().getName());
        
        this.context = context;
        
        String packageName = this.getProperty(BundleService.PACKAGE_NAME);
        if (packageName == null)
        	this.setProperty(BundleService.PACKAGE_NAME, packageName = ClassFinderActivator.getPackageName(this.getClass().getName(), false));

		String objectClass = this.getProperty(BundleService.INTERFACE);
		if (objectClass == null)
			objectClass = this.getClass().getName();
        //?String type = properties.get(OsgiService.TYPE);

		try {
			context.addServiceListener(this, ClassServiceUtility.addToFilter(null, "objectClass", objectClass));
			// ClassServiceUtility.addToFilter(filter, BundleService.PACKAGE_NAME, packageName);
		} catch (InvalidSyntaxException e) {
			e.printStackTrace();
		}
		
        serviceRegistration = context.registerService(objectClass, this, properties);
    }    
    /**
     * Bundle stopping.
     */
    public void stop(BundleContext context) throws Exception {
        ClassServiceUtility.log(context, LogService.LOG_INFO, "Stopping a BaseBundleService bundle");
//        Automatically unregistered.
        this.context = null;
    }

    @Override
    public void serviceChanged(ServiceEvent event) {
    	// Override this to monitor event  if (event.getType() == ServiceEvent.REGISTERED)
    }
    
    /**
     * Get the properties.
     * @return the properties.
     */
    public void setProperties(Dictionary<String,String> properties)
    {
        this.properties = properties;
    }
    /**
     * Get the properties.
     * @return the properties.
     */
    public Dictionary<String,String> getProperties()
    {
        return properties;
    }
	/**
	 * Get the properties.
	 * @return the properties.
	 */
	public void setProperty(String key, String value)
	{
		if (properties == null)
			properties = new Hashtable<String, String>();
		properties.put(key, value);
	}
	/**
	 * Get the properties.
	 * @return the properties.
	 */
	public String getProperty(String key)
	{
		if (properties == null)
			return null;
		return properties.get(key);
	}
	/**
	 * Given this class name, create the Class.
	 * @param className The full class name.
	 * @return The class or null if not found.
	 */
	public Class<?> makeClass(String className)
		throws ClassNotFoundException
	{
		return Class.forName(className);
	}
	/**
	 * Get the URL to the resource with this name.
	 * @param name The full resource path.
	 * @return The resource URL (usually bundle:more).
	 */
    public URL getResource(String name)
    {
    	return BaseBundleService.class.getClassLoader().getResource(name);
    }

    /**
     * Convert this encoded string back to a Java Object.
     * TODO This is expensive, I need to synchronize and use a static writer.
     * @param string The string to convert.
     * @return The java object.
     * @throws ClassNotFoundException 
     */
    public Object convertStringToObject(String string)
    	throws ClassNotFoundException
    {
        if ((string == null) || (string.length() == 0))
            return null;
        try {
            InputStream reader = new ByteArrayInputStream(string.getBytes(ClassService.OBJECT_ENCODING));//Constants.STRING_ENCODING));
            ObjectInputStream inStream = new ObjectInputStream(reader);
            Object obj = inStream.readObject();
            reader.close();
            inStream.close();
            return obj;
        } catch (IOException ex)    {
            ex.printStackTrace();   // Never
        }
        return null;
    }
    /**
     * Make sure the dependent services are up, then call startupService.
     * @param versionRange Bundle version
     * @param baseBundleServiceClassName
     * @return false if I'm waiting for the service to startup.
     */
    public boolean checkDependentServicesAndStartup(BundleContext bundleContext, String dependentBaseBundleClassName, String versionRange)
    {
        ServiceReference serviceReference = BaseClassFinderService.getClassServiceReference(bundleContext, dependentBaseBundleClassName, versionRange, null);
        
        if ((serviceReference != null) && ((serviceReference.getBundle().getState() & Bundle.ACTIVE) != 0))
        {    // Good, dependent service is already up; now I can start up.
            return this.startupThisService(bundleContext);
        }
    	// Dependent service has not started, so I need to start it and then listen
		try {
		    if (serviceReference == null)
		        bundleContext.addServiceListener(new DependentServiceRegisteredListener(this, bundleContext), /*"(&" +*/ "(objectClass=" + dependentBaseBundleClassName + ")");	// This will call startupThisService once the service is up
		    else
                bundleContext.addBundleListener(new DependentBundleStartupListener(this, bundleContext, serviceReference.getBundle())); // This will call startupThisService once the service is up
	    	new BundleStarter(this, bundleContext, dependentBaseBundleClassName, versionRange).start();
		} catch (InvalidSyntaxException e) {
			e.printStackTrace();
		}
    	return false;
    }
    /**
     * Start this service.
     * Override this to do all the startup.
     * @param context bundle context
     * @return true if successful.
     */
    public boolean startupThisService(BundleContext bundleContext)
    {
        return true;
    }
    /**
     * Stop this service.
     * Override this to do all the startup.
     * @param bundleService
     * @param context bundle context
     * @return true if successful.
     */
    public boolean shutdownThisService(BundleService bundleService, BundleContext context)
    {
        return true;
    }
}
