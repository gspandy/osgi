/*
 * Copyright © 2011 jbundle.org. All rights reserved.
 */
package org.jbundle.util.osgi.finder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jbundle.util.osgi.ClassService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;


/**
 * Thin specific static utility methods.
 */
public class ClassServiceUtility
	implements ClassService
{

    static ClassService classService = null;
    public static ClassService getClassService()
    {
        if (classService == null)
            classService = new ClassServiceUtility();
        return classService;
    }

    /**
     * Get the Osgi class service.
     * NOTE: Don't import this package as the ClassService class may not be available until this service is started.
     * @return
     */
    public static boolean classServiceAvailable = true;
    public org.jbundle.util.osgi.ClassFinder getClassFinder(Object context)
    {
        if (!classServiceAvailable)
            return null;
        try {
            Class.forName("org.osgi.framework.BundleActivator");	// This tests to see if osgi exists
            return (org.jbundle.util.osgi.ClassFinder)org.jbundle.util.osgi.finder.ClassFinderActivator.getClassFinder(context, -1);
        } catch (ClassNotFoundException ex) {
            classServiceAvailable = false;	// Osgi is not installed, no need to keep trying
            return null;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }
    /**
     * Create this object given the class name.
     * @param className
     * @return
     */
    public Object makeObjectFromClassName(String className)
    {
        return this.makeObjectFromClassName(className, null, false);
    }
    /**
     * Create this object given the class name.
     * @param className
     * @return
     */
    public Object makeObjectFromClassName(String className, String version, boolean bErrorIfNotFound) throws RuntimeException
    {
        if (className == null)
            return null;
        className = ClassServiceUtility.getFullClassName(className);

        Class<?> clazz = null;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            if (this.getClassFinder(null) != null)
                clazz = this.getClassFinder(null).findClass(className, version);	// Try to find this class in the obr repos
            if (clazz == null)
                if (bErrorIfNotFound)
                    throw new RuntimeException(e.getMessage());
        }

        Object object = null;
        try {
            if (clazz != null)
                object = clazz.newInstance();
        } catch (InstantiationException e)   {
            if (bErrorIfNotFound)
                throw new RuntimeException(e.getMessage());
        } catch (IllegalAccessException e)   {
            if (bErrorIfNotFound)
                throw new RuntimeException(e.getMessage());
        } catch (Exception e) {
            if (bErrorIfNotFound)
                throw new RuntimeException(e.getMessage());
        }
        return object;
    }
    /**
     * Create this object given the class name.
     * @param filepath
     * @return
     */
    public URL getResourceURL(String filepath, URL urlCodeBase, String version, ClassLoader classLoader) throws RuntimeException
    {
        if (filepath == null)
            return null;

        boolean isResource = true;
        if (urlCodeBase != null)
        	if ("file".equalsIgnoreCase(urlCodeBase.getProtocol()))
        		isResource = false;
        URL url = null;
        try {
        	if (isResource)
        		if (classLoader != null)
        			url = classLoader.getResource(filepath);
        } catch (Exception e) {
            // Keep trying
        }

        if (url == null)
        {
        	if (isResource)
        		if (this.getClassFinder(null) != null)
        			url = this.getClassFinder(null).findResourceURL(filepath, version);	// Try to find this class in the obr repos
        }

        if (url == null)
        {
            try
            {
                if (urlCodeBase != null)
                    url = new URL(urlCodeBase, filepath);
            } catch(MalformedURLException ex) {
                // Keep trying
            } catch (Exception e) {
                // Keep trying
            }
        }
        return url;
    }
    /**
     * Gets a resource bundle using the specified base name and locale,
     * @param locale the locale for which a resource bundle is desired
     * @param baseName the base name of the resource bundle, a fully qualified class name
     * @exception NullPointerException if <code>baseName</code> or <code>locale</code> is <code>null</code>
     * @exception MissingResourceException if no resource bundle for the specified base name can be found
     * @return a resource bundle for the given base name and locale
     */
    public final ResourceBundle getResourceBundle(String className, Locale locale, String version, ClassLoader classLoader) throws MissingResourceException
    {
        MissingResourceException ex = null;
        ResourceBundle resourceBundle = null;
        try {
            resourceBundle = ResourceBundle.getBundle(className, locale);
        } catch (MissingResourceException e) {
            ex = e;
        }

        if (resourceBundle == null)
        {
            try {
                if (this.getClassFinder(null) != null)
                    resourceBundle = this.getClassFinder(null).findResourceBundle(className, locale, version);	// Try to find this class in the obr repos
            } catch (MissingResourceException e) {
                ex = e;
            }
        }

        if (resourceBundle == null)
            if (ex != null)
                throw ex;

        return resourceBundle;
    }
    /**
     * Convert this encoded string back to a Java Object.
     * TODO This is expensive, I need to synchronize and use a static writer.
     * @param string The string to convert.
     * @return The java object.
     */
    public Object convertStringToObject(String string, String version) throws ClassNotFoundException
    {
        if ((string == null) || (string.length() == 0))
            return null;

        Object object  = null;
        try {
            InputStream reader = new ByteArrayInputStream(string.getBytes(OBJECT_ENCODING));//Constants.STRING_ENCODING));
            ObjectInputStream inStream = new ObjectInputStream(reader);
            object = inStream.readObject();
            reader.close();
            inStream.close();
        } catch (ClassNotFoundException e) {
            if (this.getClassFinder(null) != null)
            {
                String className = null;
                int startClass = e.getMessage().indexOf('\'') + 1;
                int endClass = e.getMessage().indexOf('\'', startClass);
                if (endClass != -1)
                    className = e.getMessage().substring(startClass, endClass);
                object = this.getClassFinder(null).findConvertStringToObject(className, version, string);	// Try to find this class in the obr repos
            }
            if (object == null)
                throw e;
        } catch (IOException e) {
            e.printStackTrace();    // Never
        }

        return object;
    }
    /**
     * Get the bundle classloader for this package.
     * @param string The class name to find the bundle for.
     * @return The class loader.
     * @throws ClassNotFoundException
     */
    public ClassLoader getBundleClassLoader(String packageName, String version) throws ClassNotFoundException
    {
        if ((packageName == null) || (packageName.length() == 0))
            return null;

        ClassLoader classLoader  = null;
        if (this.getClassFinder(null) != null)
        	classLoader = this.getClassFinder(null).findBundleClassLoader(packageName, version);	// Try to find this class in the obr repos

        return classLoader;
    }
    /**
     * If class name starts with '.' append base package.
     */
    public static String getFullClassName(String className) {
        return ClassServiceUtility.getFullClassName(null, className);
    }
    /**
     * If class name starts with '.' append base package.
     */
    public static String getFullClassName(String packageName, String className) {
    	return ClassServiceUtility.getFullClassName(null, packageName, className);
    }
    /**
     * If class name starts with '.' append base package.
     */
    public static String getFullClassName(String domainName, String packageName, String className) {
        if (packageName != null)
            if (packageName.length() > 0) {
                if (packageName.charAt(packageName.length() - 1) != '.')
                    packageName = packageName + '.';
            }
        if (className != null)
            if (className.length() > 0) {
                if (className.indexOf('.') == -1)
                    if (packageName != null)
                        className = packageName + className;
                if (className.charAt(0) == '.')
                {
                	if (domainName != null)
                	{
                		if (domainName.endsWith("."))
                			className = domainName + className.substring(1);
                		else
                			className = domainName + className;
                	}
                	else
                        className = ClassService.ROOT_PACKAGE + className.substring(1);
                }
            }
        return className;
    }
    /**
     * Shutdown the bundle for this service.
     * @param service The service object
     */
    public void shutdownService(Object service)
    {
        if (this.getClassFinder(null) != null)
            this.getClassFinder(null).shutdownService(service);   // Shutdown the bundle for this service
    }

    /**
     * Log this message.
     * @param context
     * @param level
     * @param message
     */
    public static boolean log(BundleContext context, int level, String message)
    {
        if (context == null)
            return false;
        ServiceReference reference = context.getServiceReference(LogService.class.getName());
        if (reference != null)
        {
            LogService logging = (LogService)context.getService(reference);
            if (logging != null)
            {
                logging.log(level, message);
                return true;
            }
        }
        return false;
    }
    
    /**
     * TODO - Need to make logging work under OSGi.
     * @param name
     * @param resourceBundleName
     * @return
     */
    public Logger getOsgiLogger(String name, String resourceBundleName)
    {
    	return new OsgiLogger(name, resourceBundleName);
    }
    
    class OsgiLogger extends Logger
    {

		protected OsgiLogger(String name, String resourceBundleName) {
			super(name, resourceBundleName);
		}
		
	    public void log(Level level, String msg) {
	    	int osgiLevel = LogService.LOG_INFO;
	    	if (level == Level.INFO)
	    		osgiLevel = LogService.LOG_INFO;
	    	if (level == Level.WARNING)
	    		osgiLevel = LogService.LOG_WARNING;
	    	if (level == Level.SEVERE)
	    		osgiLevel = LogService.LOG_ERROR;
	    	if (level == Level.CONFIG)
	    		osgiLevel = LogService.LOG_DEBUG;
	    	ClassServiceUtility.log(null, osgiLevel, msg);
	    }
    }
}
