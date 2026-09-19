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
	private static final String VERSION = "4"; //$NON-NLS-1$

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
			  var FLOAT_ID = 'egerrit-open-in-eclipse-floating';
			  var LOGO = '__EGERrit_LOGO__';
			  var pending = false;
			  var scanning = false;

			  function isInjected(anchor) {
			    var next = anchor.nextElementSibling;
			    return !!next && !!next.classList && next.classList.contains(MARK);
			  }

			  function isMagicPath(path) {
			    return !!path && (path.charAt(0) === '/' || path === 'COMMIT_MSG' || path === 'MERGE_LIST');
			  }

			  function pathFromUrl() {
			    var url = window.location.href;
			    var marker = url.indexOf('/+/');
			    if (marker < 0) {
			      return null;
			    }
			    var rest = url.substring(marker + 3);
			    var firstSlash = rest.indexOf('/');
			    if (firstSlash < 0) {
			      return null;
			    }
			    var secondSlash = rest.indexOf('/', firstSlash + 1);
			    if (secondSlash < 0) {
			      return null;
			    }
			    var path = rest.substring(secondSlash + 1);
			    var end = path.length;
			    var query = path.indexOf('?');
			    if (query >= 0 && query < end) {
			      end = query;
			    }
			    var fragment = path.indexOf('#');
			    if (fragment >= 0 && fragment < end) {
			      end = fragment;
			    }
			    path = path.substring(0, end);
			    try {
			      path = decodeURIComponent(path);
			    } catch (error) {
			      //Keep the raw path
			    }
			    return path;
			  }

			  function createButton(getLink) {
			    var button = document.createElement('span');
			    button.className = MARK;
			    button.title = 'Open this file in the Eclipse editor';
			    button.style.cssText = 'display:inline-flex;align-items:center;margin-left:4px;margin-right:4px;cursor:pointer;vertical-align:middle;flex:none;position:relative;top:-1px;';
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
			      if (link) {
			        var bridged = false;
			        if (typeof egerritOpenFile === 'function') {
			          try {
			            egerritOpenFile(link);
			            bridged = true;
			          } catch (error) {
			            bridged = false;
			          }
			        }
			        if (!bridged) {
			          //Fallback not relying on the JavaScript bridge: the URL is
			          //intercepted by the location listener of the browser
			          try {
			            window.location.href = 'egerrit-open-in-eclipse:' + encodeURIComponent(link);
			          } catch (error) {
			            //Nothing else can be tried
			          }
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

			  function injectInFilePath(link, getLink) {
			    if (!link) {
			      return;
			    }
			    var copy = null;
			    for (var i = 0; i < link.children.length; i++) {
			      var child = link.children[i];
			      if (child.classList && child.classList.contains(MARK)) {
			        //The button is already there
			        return;
			      }
			      if (child.tagName === 'GR-COPY-CLIPBOARD') {
			        copy = child;
			      }
			    }
			    //Insert the button right after the file name and before the "copy" icon of Gerrit
			    var button = createButton(getLink);
			    if (copy) {
			      link.insertBefore(button, copy);
			    } else {
			      link.appendChild(button);
			    }
			  }

			  function injectInDropdownTrigger(dropdown, getLink) {
			    if (!dropdown || !dropdown.shadowRoot) {
			      return null;
			    }
			    var trigger = dropdown.shadowRoot.querySelector('gr-button#trigger');
			    if (!trigger) {
			      return null;
			    }
			    var copy = null;
			    for (var i = 0; i < trigger.children.length; i++) {
			      var child = trigger.children[i];
			      if (child.classList && child.classList.contains(MARK)) {
			        //The button is already there
			        return trigger;
			      }
			      if (child.tagName === 'GR-COPY-CLIPBOARD') {
			        copy = child;
			      }
			    }
			    //Insert the button right after the file name and before the "copy" icon of Gerrit
			    var button = createButton(getLink);
			    if (copy) {
			      trigger.insertBefore(button, copy);
			    } else {
			      trigger.appendChild(button);
			    }
			    return trigger;
			  }

			  function removeButtonAfter(element) {
			    if (!element) {
			      return;
			    }
			    var next = element.nextElementSibling;
			    if (next && next.classList && next.classList.contains(MARK)) {
			      next.parentNode.removeChild(next);
			    }
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
			        var rowPath = null;
			        try {
			          rowPath = JSON.parse(row.getAttribute('data-file')).path;
			        } catch (error) {
			          rowPath = null;
			        }
			        if (isMagicPath(rowPath)) {
			          //Magic files (commit message, merge list, ...) do not exist in the workspace
			          return;
			        }
			        var anchor = row.querySelector('span.path a.pathLink');
			        injectInFilePath(anchor, function () {
			          return anchor.getAttribute('href');
			        });
			      })(rows[i]);
			    }

			    //Header of the diff view: the button uses the URL of the page, so it follows
			    //the previous/next navigation and the file selection
			    if (!isMagicPath(pathFromUrl())) {
			      var dropdowns = root.querySelectorAll('.jumpToFileContainer > gr-dropdown-list');
			      for (var j = 0; j < dropdowns.length; j++) {
			        (function (dropdown) {
			          var trigger = injectInDropdownTrigger(dropdown, function () {
			            return window.location.href;
			          });
			          if (trigger) {
			            //Remove the button injected next to the dropdown by an older version
			            removeButtonAfter(dropdown);
			          } else {
			            //Fallback for a different Gerrit DOM: insert the button after the dropdown
			            injectAfter(dropdown, function () {
			              return window.location.href;
			            });
			          }
			        })(dropdowns[j]);
			      }
			    }

			    var elements = root.querySelectorAll('*');
			    for (var k = 0; k < elements.length; k++) {
			      if (elements[k].shadowRoot) {
			        scan(elements[k].shadowRoot);
			      }
			    }
			  }

			  function looksLikeFileUrl() {
			    //True when the URL is a file URL: /c/<project>/+/<change>/<patchset>/<path>
			    var url = window.location.href;
			    var marker = url.indexOf('/+/');
			    if (marker < 0) {
			      return false;
			    }
			    var rest = url.substring(marker + 3);
			    var firstSlash = rest.indexOf('/');
			    if (firstSlash < 0) {
			      return false;
			    }
			    rest = rest.substring(firstSlash + 1);
			    var secondSlash = rest.indexOf('/');
			    return secondSlash >= 0 && secondSlash < rest.length - 1;
			  }

			  function injectFloatingButton() {
			    if (document.getElementById(FLOAT_ID)) {
			      return;
			    }
			    if (!document.body || !looksLikeFileUrl() || isMagicPath(pathFromUrl())) {
			      return;
			    }
			    var button = createButton(function () {
			      return window.location.href;
			    });
			    button.id = FLOAT_ID;
			    button.title = 'Open this file in the Eclipse editor';
			    button.style.cssText = 'position:fixed;right:16px;bottom:16px;z-index:2147483647;'
			      + 'display:inline-flex;align-items:center;cursor:pointer;padding:6px;border-radius:4px;'
			      + 'background:rgba(128,128,128,0.15);box-shadow:0 1px 4px rgba(0,0,0,0.3);';
			    document.body.appendChild(button);
			  }

			  function scanAll() {
			    if (scanning) {
			      return;
			    }
			    scanning = true;
			    try {
			      scan(document);
			      if (document.querySelectorAll('.' + MARK).length === 0) {
			        //No button could be injected in the Gerrit UI: add a floating one so
			        //that the feature remains usable even if the Gerrit DOM changes
			        injectFloatingButton();
			      }
			    } catch (error) {
			      //Never break the page
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

			  function consume(event) {
			    event.preventDefault();
			    event.stopPropagation();
			    if (event.stopImmediatePropagation) {
			      event.stopImmediatePropagation();
			    }
			  }

			  function installNavigation() {
			    if (window.__egerritNavigationInstalled) {
			      return;
			    }
			    window.__egerritNavigationInstalled = true;
			    //Previous/next buttons of the mouse, when the browser reports them in the DOM
			    //(DOM buttons 3 and 4 per the UI Events specification). The event must be
			    //consumed, otherwise it reaches the workbench of Eclipse.
			    document.addEventListener('mousedown', function (event) {
			      if (event.button === 3) {
			        consume(event);
			        window.history.back();
			      } else if (event.button === 4) {
			        consume(event);
			        window.history.forward();
			      }
			    }, true);
			    //Alt+Left/Alt+Right, also used by the previous/next buttons of some mice. In
			    //Eclipse these shortcuts are bound to the previous/next editor: the event must
			    //be consumed so that the editor is not switched.
			    document.addEventListener('keydown', function (event) {
			      if (event.altKey && event.key === 'ArrowLeft') {
			        consume(event);
			        window.history.back();
			      } else if (event.altKey && event.key === 'ArrowRight') {
			        consume(event);
			        window.history.forward();
			      }
			    }, true);
			  }

			  installNavigation();
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
