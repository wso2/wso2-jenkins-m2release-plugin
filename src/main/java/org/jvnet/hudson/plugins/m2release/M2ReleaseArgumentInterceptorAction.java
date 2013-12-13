/*
 * The MIT License
 * 
 * Copyright (c) 2011, Dominik Bartholdi
 * Copyright (c) 2013, Robert Kleinschmager
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jvnet.hudson.plugins.m2release;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import hudson.maven.MavenArgumentInterceptorAction;
import hudson.maven.MavenModuleSetBuild;
import hudson.util.ArgumentListBuilder;

/**
 * This action provides the arguments to trigger maven in case of a release
 * build.
 * 
 * @author Dominik Bartholdi
 * @author Robert Kleinschmager
 * @version 0.9.0
 */
public class M2ReleaseArgumentInterceptorAction implements MavenArgumentInterceptorAction {


    private static final Logger LOGGER = Logger.getLogger(M2ReleaseArgumentInterceptorAction.class.getName());

	
	private final transient String goalsAndOptions;
	@Deprecated
	private transient boolean isDryRun; // keep backward compatible
    private final transient String scmPassword;

    @Deprecated
	public M2ReleaseArgumentInterceptorAction(String goalsAndOptions) {
        this(goalsAndOptions, null);
    }

    public M2ReleaseArgumentInterceptorAction(String goalsAndOptions, String scmPassword) {
		this.goalsAndOptions = goalsAndOptions;
        this.scmPassword = scmPassword;
	}

	public String getIconFileName() {
		return null;
	}

	public String getDisplayName() {
		return null;
	}

	public String getUrlName() {
		return null;
	}

	public String getGoalsAndOptions(MavenModuleSetBuild build) {
		return goalsAndOptions;
	}

	public ArgumentListBuilder intercept(ArgumentListBuilder mavenargs, MavenModuleSetBuild build) {
		
		// calling internal Method, which now (without MavenModuleSetBuil) can be tested easily
		return internalIntercept(mavenargs, build.getProject().isIncrementalBuild());
	}
	
	
	ArgumentListBuilder internalIntercept(ArgumentListBuilder mavenArgumentListBuilder, boolean isIncrementalBuild) {
		
		ArgumentListBuilder returnListBuilder = new ArgumentListBuilder();
		List<String> argumentList = mavenArgumentListBuilder.toList();
		
		if (isIncrementalBuild && containsJenkinsIncrementalBuildArguments(argumentList))
		{
			LOGGER.config("This Maven build seems to be configured as 'Incremental build'. This will be disables, as always the full project will be released");
			returnListBuilder = removeAllIncrementalBuildArguments(mavenArgumentListBuilder.clone());
		} else
		{
			returnListBuilder = mavenArgumentListBuilder.clone();
		}

        if (scmPassword != null) {
            returnListBuilder.addMasked("-Dpassword=" + scmPassword);
        }
		
		return returnListBuilder;
	}
	
	
	/**
	 * tries to assume, if jenkins itself added some parameters to the argument list, which cause a maven multi module project to be build incremental
	 * @param mavenargs
	 * @param build
	 * @return
	 * 
	 * @see 
	 */
	private boolean containsJenkinsIncrementalBuildArguments(List<String> mavenargs) {
		int amdIndex = mavenargs.indexOf("-amd");
		int plIndex = mavenargs.indexOf("-pl");
		
		boolean amdArgumentExists = amdIndex != -1;
		boolean plArgumentExists = plIndex != -1;
		
		if (amdArgumentExists && plArgumentExists) 
		{
			boolean amdAndPlArgumentAreInSupposedOrder = amdIndex == plIndex-1;
			// assuming, that the argument behind -pl is the list of projects, as added in {@link MavenModuleSetBuild}
			
			return amdAndPlArgumentAreInSupposedOrder && thereIsAnArgumentBehinPlArgument(mavenargs, plIndex);
		} else {
			return false;
			
		}		
	}
	
	private boolean thereIsAnArgumentBehinPlArgument(List<String> mavenargs, int plIndex) {
		if (mavenargs.size() >= plIndex+1)
		{
			return mavenargs.get(plIndex+1) != null;
		}		
		return false;
	}
	
	
	private ArgumentListBuilder removeAllIncrementalBuildArguments(
			ArgumentListBuilder mavenargs) {
		
		// remove the three elements which was added by MavenModuleSetBuild
		// -amd
		// -pl
		// <list of modules>
		LOGGER.finer("Start removing the arguments '-amd -pl <list of modules>' from argument list");
		
		ArgumentListBuilder returnListBuilder = new ArgumentListBuilder();
		
		int amdIndex = mavenargs.toList().indexOf("-amd");
		
		ensureArgumentsAndMaskHaveSaveSize(mavenargs);

		boolean[] maskArray = mavenargs.toMaskArray();
		ArrayList<Boolean> maskList = Lists.newArrayList();
		for (boolean b : maskArray) {
			maskList.add(Boolean.valueOf(b));
		}
		
		List<String> oldArgumentList = mavenargs.toList();

		// as List.remove is shifting all elements, we can reuse the index
		String removedAmd = oldArgumentList.remove(amdIndex);
		Preconditions.checkArgument("-amd".equals(removedAmd));
		maskList.remove(amdIndex);
		
		String removedPl = oldArgumentList.remove(amdIndex);
		Preconditions.checkArgument("-pl".equals(removedPl));			
		maskList.remove(amdIndex);
		
		String removedModuleList = oldArgumentList.remove(amdIndex);
		maskList.remove(amdIndex);
		LOGGER.finer(String.format("Removed the arguments '-amd -pl %s' from argument list", removedModuleList));
		
		// rebuild
		for (int i=0; i < oldArgumentList.size() ; i++) {
				returnListBuilder.add(oldArgumentList.get(i), maskList.get(i).booleanValue());
		}

		ensureArgumentsAndMaskHaveSaveSize(returnListBuilder);
		
		LOGGER.fine(String.format("Rebuild maven argument list, old size=%s; new size=%s", oldArgumentList.size(), returnListBuilder.toList().size()));
		
		return returnListBuilder;
	}
	
	private void ensureArgumentsAndMaskHaveSaveSize(ArgumentListBuilder alb) {
		if (alb.toList().size() != alb.toMaskArray().length)
		{
			throw new RuntimeException("could not intercept argument list: ArgumentList and Mask are out of sync ");
		}
	}
	
	

}
