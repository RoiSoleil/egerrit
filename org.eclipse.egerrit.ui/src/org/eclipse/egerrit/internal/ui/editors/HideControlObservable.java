/*******************************************************************************
 * Copyright (c) 2016 Ericsson AB.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Ericsson AB - initial API and implementation
 *******************************************************************************/
package org.eclipse.egerrit.internal.ui.editors;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.observable.value.IValueChangeListener;
import org.eclipse.core.databinding.observable.value.ValueChangeEvent;
import org.eclipse.core.databinding.observable.value.WritableValue;
import org.eclipse.jface.databinding.swt.ISWTObservableValue;
import org.eclipse.jface.databinding.swt.typed.WidgetProperties;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Control;

/**
 * Observable to control the presence (visibility and size) of any control within a grid layout.
 * <p>
 * Changing the value of this observable will do two things:
 * <ol>
 * <li>Set the visibility of the control</li>
 * <li>Set the size of the control to zero when the control is invisible.</li>
 * </ol>
 * So, when using this observable, the control will not only be invisible, but it will be gone, completely. Normally,
 * when setting the visibility of a control to <code>false</code>, the control will not be displayed but will still take
 * all the space on the screen.
 * </p>
 * <p>
 * <strong>Note:</strong> this observable works for controls within a <strong>GridLayout only</strong>.
 * </p>
 */
class HideControlObservable extends WritableValue implements IValueChangeListener {
	private final DataBindingContext bindingContext = new DataBindingContext();

	private final ISWTObservableValue sizeObservable;

	private final Point size = new Point(0, 0);

	private final Control control;

	HideControlObservable(Control control) {
		super(control.getVisible(), Boolean.class);
		this.control = control;

		UpdateValueStrategy never = new UpdateValueStrategy(UpdateValueStrategy.POLICY_NEVER);
		bindingContext.bindValue(WidgetProperties.visible().observe(control), this, never, null);

		sizeObservable = WidgetProperties.size().observe(control);
		sizeObservable.addValueChangeListener(this);

		if (!control.isVisible()) {
			GridData gd = (GridData) control.getLayoutData();
			if (gd == null) {
				gd = new GridData();
			}
			gd.exclude = true;
			control.setLayoutData(gd);
			control.setSize(new Point(0, 0));
		}
	}

	@Override
	public void doSetValue(Object value) {
		super.doSetValue(value);
		Boolean bool = (Boolean) value;
		if (bool) {
			GridData gd = (GridData) control.getLayoutData();
			if (gd == null) {
				gd = new GridData();
			}
			gd.exclude = false;
			control.setLayoutData(gd);
			control.setSize(size);
			control.getParent().layout();
		} else {
			GridData gd = (GridData) control.getLayoutData();
			if (gd == null) {
				gd = new GridData();
			}
			gd.exclude = true;
			control.setLayoutData(gd);
			control.setSize(new Point(0, 0));
			control.getParent().layout();
		}
	}

	@Override
	public synchronized void dispose() {
		bindingContext.dispose();
		sizeObservable.dispose();
		super.dispose();
	}

	@Override
	public void handleValueChange(ValueChangeEvent event) {
		Point newSize = (Point) event.getObservableValue().getValue();
		if (newSize.x > size.x) {
			size.x = newSize.x;
		}
		if (newSize.y > size.y) {
			size.y = newSize.y;
		}
	}
}