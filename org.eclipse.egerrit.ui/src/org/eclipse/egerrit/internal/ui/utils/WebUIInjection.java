/*******************************************************************************
 * Copyright (c) 2026 Ericsson AB.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Ericsson AB - initial API and implementation
 *******************************************************************************/

package org.eclipse.egerrit.internal.ui.utils;

/**
 * Builds the JavaScript snippet injected in the Gerrit web UI to add an "Open in Eclipse" button next to the files.
 * <p>
 * The script is designed to survive the single page application navigations of Gerrit:
 * <ul>
 * <li>the buttons are (re-)injected when the DOM changes (MutationObserver) and at a regular interval,</li>
 * <li>the link opened by a button is resolved when the button is clicked, so that the file currently displayed is
 * always the one opened (including after a click on the previous/next navigation links),</li>
 * <li>the Gerrit DOM is looked up across the shadow roots of the custom elements.</li>
 * </ul>
 */
public final class WebUIInjection {

	/** Version of the script; it has to be increased when the script changes. */
	private static final String VERSION = "1"; //$NON-NLS-1$

	private static final String LOGO_PLACEHOLDER = "__EGERrit_LOGO__"; //$NON-NLS-1$

	private static final String VERSION_PLACEHOLDER = "__EGERrit_VERSION__"; //$NON-NLS-1$

	private WebUIInjection() {
	}

	/**
	 * Return the JavaScript snippet to inject in the browser.
	 *
	 * @param logo
	 *            the URL of the image displayed in the injected buttons (a data URL)
	 * @return the script
	 */
	public static String getScript(String logo) {
		return SCRIPT.replace(LOGO_PLACEHOLDER, logo != null ? logo : "").replace(VERSION_PLACEHOLDER, VERSION); //$NON-NLS-1$
	}

	private static final String SCRIPT = """
			(function () {
			  var VERSION = '__EGERrit_VERSION__';
			  if (window.__egerritInjectedVersion === VERSION) {
			    if (window.__egerritScan) {
			      window.__egerritScan();
			    }
			    return;
			  }
			  window.__egerritInjectedVersion = VERSION;
			  var MARK = 'egerrit-open-in-eclipse';
			  var LOGO = '__EGERrit_LOGO__';
			  var pending = false;
			  var scanning = false;

			  function isInjected(anchor) {
			    var next = anchor.nextElementSibling;
			    return !!next && !!next.classList && next.classList.contains(MARK);
			  }

			  function createButton(getLink) {
			    var button = document.createElement('span');
			    button.className = MARK;
			    button.title = 'Open this file in the Eclipse editor';
			    button.style.cssText = 'display:inline-flex;align-items:center;margin-left:6px;cursor:pointer;vertical-align:middle;flex:none;';
			    if (LOGO) {
			      var image = document.createElement('img');
			      image.src = LOGO;
			      image.alt = 'Open in Eclipse';
			      image.width = 14;
			      image.height = 14;
			      image.style.cssText = 'display:block;';
			      button.appendChild(image);
			    } else {
			      button.textContent = 'Eclipse';
			    }
			    button.addEventListener('mousedown', function (event) {
			      event.preventDefault();
			      event.stopPropagation();
			    }, true);
			    button.addEventListener('click', function (event) {
			      event.preventDefault();
			      event.stopPropagation();
			      var link = null;
			      try {
			        link = getLink();
			      } catch (error) {
			        link = null;
			      }
			      if (link && typeof egerritOpenFile === 'function') {
			        try {
			          egerritOpenFile(link);
			        } catch (error) {
			          //The bridge is not available, ignore
			        }
			      }
			    }, true);
			    return button;
			  }

			  function injectAfter(anchor, getLink) {
			    if (!anchor || !anchor.parentNode || isInjected(anchor)) {
			      return;
			    }
			    anchor.parentNode.insertBefore(createButton(getLink), anchor.nextElementSibling);
			  }

			  function observe(root) {
			    if (root.__egerritObserved || typeof MutationObserver !== 'function') {
			      return;
			    }
			    root.__egerritObserved = true;
			    new MutationObserver(scheduleScan).observe(root, {childList: true, subtree: true});
			  }

			  function scan(root) {
			    observe(root);

			    //File list rows of the change page
			    var rows = root.querySelectorAll('div.file-row[data-file]');
			    for (var i = 0; i < rows.length; i++) {
			      (function (row) {
			        var anchor = row.querySelector('span.path a.pathLink');
			        injectAfter(anchor, function () {
			          return anchor.getAttribute('href');
			        });
			      })(rows[i]);
			    }

			    //Header of the diff view: the button uses the URL of the page, so it follows
			    //the previous/next navigation and the file selection
			    var dropdowns = root.querySelectorAll('.jumpToFileContainer > gr-dropdown-list');
			    for (var j = 0; j < dropdowns.length; j++) {
			      (function (dropdown) {
			        injectAfter(dropdown, function () {
			          return window.location.href;
			        });
			      })(dropdowns[j]);
			    }

			    var elements = root.querySelectorAll('*');
			    for (var k = 0; k < elements.length; k++) {
			      if (elements[k].shadowRoot) {
			        scan(elements[k].shadowRoot);
			      }
			    }
			  }

			  function scanAll() {
			    if (scanning) {
			      return;
			    }
			    scanning = true;
			    try {
			      scan(document);
			    } finally {
			      scanning = false;
			    }
			  }

			  function scheduleScan() {
			    if (pending) {
			      return;
			    }
			    pending = true;
			    setTimeout(function () {
			      pending = false;
			      scanAll();
			    }, 250);
			  }

			  window.__egerritScan = scanAll;
			  window.addEventListener('hashchange', scheduleScan);
			  window.addEventListener('popstate', scheduleScan);
			  //Gerrit uses the History API for its navigation: make sure the scan is
			  //triggered when the page is navigated programmatically
			  try {
			    var wrap = function (method) {
			      return function () {
			        var result = method.apply(this, arguments);
			        scheduleScan();
			        return result;
			      };
			    };
			    window.history.pushState = wrap(window.history.pushState);
			    window.history.replaceState = wrap(window.history.replaceState);
			  } catch (error) {
			    //Ignore: the periodic scan is still in place
			  }
			  scanAll();
			  setInterval(scanAll, 3000);
			})();
			"""; //$NON-NLS-1$
}
