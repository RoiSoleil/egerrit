package org.eclipse.egerrit.internal.ui.utils;

/*******************************************************************************
 * Copyright (c) 2011 - 2015 SAP and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Sascha Scholz (SAP) - initial API and implementation
 *     Tasktop Technologies - improvements
 *     Ericsson	- improvements
 *******************************************************************************/

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.egit.core.RepositoryCache;
import org.eclipse.egit.core.RepositoryUtil;
import org.eclipse.egit.ui.internal.ResourcePropertyTester;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

public class GerritToGitMapping {
	//The name of the gerrit project we are looking for
	private final String gerritProject;

	//The daemon address as provided by the gerrit server
	private final URIish daemonURL;

	//Represents a potential match
	private Repository match;

	//An approximation of what we are looking for.
	private String addressOfSearchedRepo;

	//Indicate whether we are looking in the pushURIs of the repo
	private boolean inPushRepos = false;

	public GerritToGitMapping(URIish daemonURL, String gerritProject) {
		if (gerritProject == null) {
			throw new IllegalArgumentException("gerritProject can't be null"); //$NON-NLS-1$
		}
		if (daemonURL == null) {
			throw new IllegalArgumentException("daemonURL can't be null"); //$NON-NLS-1$
		}
		this.daemonURL = daemonURL;
		this.gerritProject = gerritProject;
	}

	/**
	 * Look for a local git repo that would correspond to the gerrit project
	 *
	 * @return the repository matching
	 * @throws IOException
	 */
	public Repository find() throws IOException {
		//We first search against the remote URIs
		addressOfSearchedRepo = buildSearchedAddress(true);
		findMatchingRepository();
		if (match != null) {
			return match;
		}
		//Now we search against the push URIs
		inPushRepos = true;
		addressOfSearchedRepo = buildSearchedAddress(false);
		findMatchingRepository();
		if (match != null) {
			return match;
		}
		findByName();
		return match;
	}

	private void findByName() throws IOException {
		RepositoryUtil repoUtil = getRepositoryUtil();
		RepositoryCache repoCache = getRepositoryCache();
		for (String dirs : repoUtil.getConfiguredRepositories()) {
			match = repoCache.lookupRepository(new File(dirs));
			if (!ResourcePropertyTester.hasGerritConfiguration(match)) {
				continue;
			}
			RemoteConfig remote = findRemoteByName();
			if (remote != null) {
				return;
			}
		}
		match = null;
	}

	private RemoteConfig findRemoteByName() {
		Assert.isNotNull(match);
		List<RemoteConfig> remotes;
		try {
			remotes = RemoteConfig.getAllRemoteConfigs(match.getConfig());
		} catch (URISyntaxException e) {
			//TODO We should probably log something
			remotes = new ArrayList<RemoteConfig>(0);
		}
		for (RemoteConfig remote : remotes) {
			List<URIish> remoteURIs = new ArrayList<URIish>();
			remoteURIs.addAll(remote.getPushURIs());
			remoteURIs.addAll(remote.getURIs());
			for (URIish remoteURI : remoteURIs) {
				if (getInterestingSegments(remoteURI).endsWith(gerritProject)) {
					return remote;
				}
			}
		}
		return null;
	}

	//Build an approximation of the URI we are looking for
	private String buildSearchedAddress(boolean includePath) {
		if (includePath) {
			return getInterestingSegments(daemonURL) + '/' + gerritProject;
		}
		return daemonURL.getHost() + '/' + gerritProject;

	}

	private void findMatchingRepository() throws IOException {
		RepositoryUtil repoUtil = getRepositoryUtil();
		RepositoryCache repoCache = getRepositoryCache();
		for (String dirs : repoUtil.getConfiguredRepositories()) {
			match = repoCache.lookupRepository(new File(dirs));
			if (!ResourcePropertyTester.hasGerritConfiguration(match)) {
				continue;
			}
			RemoteConfig remote = findMatchingRemote();
			if (remote != null) {
				return;
			}
		}
		match = null;
	}

	private RemoteConfig findMatchingRemote() throws IOException {
		Assert.isNotNull(match);
		List<RemoteConfig> remotes;
		try {
			remotes = RemoteConfig.getAllRemoteConfigs(match.getConfig());
		} catch (URISyntaxException e) {
			//TODO We should probably log something
			remotes = new ArrayList<RemoteConfig>(0);
		}
		for (RemoteConfig remote : remotes) {
			List<URIish> remoteURIs = inPushRepos ? remote.getPushURIs() : remote.getURIs();
			for (URIish remoteURI : remoteURIs) {
				if (getInterestingSegments(remoteURI).equals(addressOfSearchedRepo)) {
					return remote;
				}
			}
		}
		return null;
	}

	//Build an approximation of the URI we are looking for
	private String getInterestingSegments(URIish uri) {
		return uri.getHost() + '/' + cleanTrailingDotGit(cleanLeadingSlash(uri.getPath()));
	}

	private static RepositoryCache getRepositoryCache() {
		return RepositoryCache.INSTANCE;
	}

	private static RepositoryUtil getRepositoryUtil() {
		return RepositoryUtil.INSTANCE;
	}

	private static String cleanLeadingSlash(String path) {
		if (path.startsWith("/")) { //$NON-NLS-1$
			return path.substring(1);
		} else {
			return path;
		}
	}

	private static String cleanTrailingDotGit(String path) {
		int dotGitIndex = path.lastIndexOf(".git"); //$NON-NLS-1$
		if (dotGitIndex >= 0) {
			return path.substring(0, dotGitIndex);
		} else {
			return path;
		}
	}
}
