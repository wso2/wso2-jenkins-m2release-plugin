/*
 * The MIT License
 * 
 * Copyright (c) 2011, Dominik Bartholdi
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
import java.util.Arrays;
import java.util.List;

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
 * @version 0.9.0
 */
public class M2ReleaseArgumentInterceptorAction implements MavenArgumentInterceptorAction {

	private String goalsAndOptions;
	@Deprecated
	private transient boolean isDryRun; // keep backward compatible

	public M2ReleaseArgumentInterceptorAction(String goalsAndOptions) {
		this.goalsAndOptions = goalsAndOptions;
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
		
		ArgumentListBuilder returnListBuilder = new ArgumentListBuilder();
		
		if (containsJenkinsIncrementalBuildArguments(mavenargs))
		{
			returnListBuilder = removeAllIncrementalBuildArguments(mavenargs.clone());
		} else
		{
			returnListBuilder = mavenargs.clone();
		}
		
		return returnListBuilder;
	}
	
	private boolean containsJenkinsIncrementalBuildArguments(ArgumentListBuilder mavenargs)
	{
		int amdIndex = mavenargs.toList().indexOf("-amd");		
		return amdIndex != -1;
	}
	
	private ArgumentListBuilder removeAllIncrementalBuildArguments(
			ArgumentListBuilder mavenargs) {
		
		// remove the three elements which was added by MavenModuleSetBuild
		// -amd
		// -pl
		// <list of modules>
		
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
		
		oldArgumentList.remove(amdIndex);
		maskList.remove(amdIndex);		
		
		// rebuild
		for (int i=0; i < oldArgumentList.size() ; i++) {
				returnListBuilder.add(oldArgumentList.get(i), maskList.get(i).booleanValue());
		}

		ensureArgumentsAndMaskHaveSaveSize(returnListBuilder);
		
		return returnListBuilder;
	}
	
	private void ensureArgumentsAndMaskHaveSaveSize(ArgumentListBuilder alb) {
		if (alb.toList().size() != alb.toMaskArray().length)
		{
			throw new RuntimeException("could not intercept argument list: ArgumentList and Mask are out of sync ");
		}
	}
	
	

}
