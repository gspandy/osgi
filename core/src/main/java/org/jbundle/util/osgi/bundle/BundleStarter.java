/*
 * Copyright © 2012 jbundle.org. All rights reserved.
 */
package org.jbundle.util.osgi.bundle;

import org.jbundle.util.osgi.finder.BaseClassFinderService;
import org.jbundle.util.osgi.finder.ClassFinderActivator;
import org.osgi.framework.BundleContext;

/**
 * This class starts the bundle that belongs to this class and then calls the classfinder
 * with the bundle object.
 * Note: This is a thread since is is usually takes a while to run.
 * @author don
 *
 */
public class BundleStarter extends Thread
{
	BundleContext bundleContext = null;
	String dependentBaseBundleClassName = null;
	String versionRange = null;
	
	public BundleStarter(BaseBundleService bundleService, BundleContext bundleContext, String dependentBaseBundleClassName, String versionRange)
	{
		this.bundleContext = bundleContext;
		this.dependentBaseBundleClassName = dependentBaseBundleClassName;
		this.versionRange = versionRange;
	}
	public void run()
	{
		BaseClassFinderService classFinder = (BaseClassFinderService)ClassFinderActivator.getClassFinder(bundleContext, -1);
		classFinder.startBaseBundle(bundleContext, dependentBaseBundleClassName, versionRange, -1);
	}
}
