package com.qing.widget;

import android.content.Context;
import android.widget.ProgressBar;

import com.qing.log.MLog;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Created by zwq on 2015/04/06 12:22.<br/><br/>
 * 横向/水平进度条
 */
public class HorizontalProgressBar extends ProgressBar {
	private static final String TAG = HorizontalLeftAlignView.class.getName();

	public HorizontalProgressBar(Context context) {
		super(context, null, android.R.attr.progressBarStyleHorizontal);
//		this(context, true);
	}
	
	public HorizontalProgressBar(Context context, boolean isHorizontal) {
		super(context);
		
		if(isHorizontal){
			setFieldValue(this, "mOnlyIndeterminate", Boolean.valueOf(false));
			setIndeterminate(false);
			setIndeterminateDrawable(getResources().getDrawable(android.R.drawable.progress_indeterminate_horizontal));
			setProgressDrawable(getResources().getDrawable(android.R.drawable.progress_horizontal));
		}
	}
	
	private void setFieldValue(final Object object, final String fieldName, final Object value) {
		Field field = getDeclaredField(object, fieldName);  
		if (field == null) {
			throw new IllegalArgumentException("Could not find field [" + fieldName + "] on target [" + object + "]");  
		}
		makeAccessible(field);  
		try {  
			field.set(object, value);  
		} catch (IllegalAccessException e) {  
			e.printStackTrace();
			MLog.e(TAG, e.getMessage());
		} 
	}
	
	private Field getDeclaredField(Object object, String fieldName) {
		return getDeclaredField(object.getClass(), fieldName);
	}
	
	protected static Field getDeclaredField(final Class<?> clazz, final String fieldName) {  
		for (Class<?> superClass = clazz; superClass != Object.class; superClass = superClass.getSuperclass()) {  
			try {  
				return superClass.getDeclaredField(fieldName);  
			} catch (NoSuchFieldException e) { 
				e.printStackTrace();
				// Field不在当前类定义,继续向上转型  
			}  
		}  
		return null;  
	}  

	private void makeAccessible(Field field) {
		if (!Modifier.isPublic(field.getModifiers()) || 
				!Modifier.isPublic(field.getDeclaringClass().getModifiers())) {  
			field.setAccessible(true);  
		} 
	}

}
