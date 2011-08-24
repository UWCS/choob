/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.co.uwcs.choob.support;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.uwcs.choob.ChoobCommand;
import uk.co.uwcs.choob.ChoobParam;
import uk.co.uwcs.choob.support.events.Message;

/**
 * Extracts parameters from a Message for a given method.
 *
 * @author benji
 */
public class ChoobCommandWrapper
{
	private static final Logger logger = LoggerFactory.getLogger(ChoobCommandWrapper.class);

	private final Method meth;

	public ChoobCommandWrapper(final Method meth)
	{
		this.meth = meth;
	}

	public String call(Object o,Message mes) throws ChoobException, CommandUsageException
	{
		try
		{
			List<Object> params =  new ArrayList<Object>();
			for (String str : mes.getMessage().split("\\s+"))
				params.add(str);
			if (params.size() > 0)
				params.remove(0);


			Annotation[][] annotations = meth.getParameterAnnotations();
			Class<?>[] paramTypes = meth.getParameterTypes();
			int numParams = 0;
			for (int i = 0; i < paramTypes.length; i++)
			{
				for (Annotation a : annotations[i])
				{
					if (a.annotationType().equals(ChoobParam.class))
					{
						numParams++;
					}
				}
				if (paramTypes[i].isAssignableFrom(Message.class))
				{
					if (i <= params.size())
					{
						numParams++;
						params.add(i,mes);
					}
				}
			}

			if (numParams == params.size())
			{
				Object result = meth.invoke(o, params.toArray());
				if (result == null)
					return "";
				else
					return result.toString();
			} else
			{
				throw new CommandUsageException(getUsage(meth));
			}
		} catch (IllegalAccessException ex)
		{
			logger.error("couldn't invoke command", ex);
			throw new ChoobException("Illegal Access Exception encountered when invoking command.",ex);
		} catch (IllegalArgumentException ex)
		{
			logger.error("couldn't invoke command", ex);
			throw new ChoobException("Illegal Argument Exception encountered when invoking command.",ex);
		} catch (InvocationTargetException ex)
		{
			logger.error("couldn't invoke command", ex);
			throw new ChoobException("Illegal Target Exception encountered when invoking command.", ex.getCause());
		}

	}

	public String getName()
	{
		return meth.getAnnotation(ChoobCommand.class).name();
	}

	/**
	 * @return help in Choob's format.
	 */
	public String[] getHelp()
	{
		List<String> helpLines = new ArrayList<String>();
		helpLines.add(meth.getAnnotation(ChoobCommand.class).help());
		List<ChoobParam> paramAnnotations = new ArrayList<ChoobParam>();
		for (Annotation[] anl : meth.getParameterAnnotations())
			for (Annotation an : anl)
				if (an.annotationType().equals(ChoobParam.class))
					paramAnnotations.add((ChoobParam)an);
		StringBuilder builder = new StringBuilder();
		for (ChoobParam param : paramAnnotations)
			builder.append("<").append(param.name()).append(">").append(" ");
		helpLines.add(builder.toString());

		for (ChoobParam param : paramAnnotations)
			helpLines.add("<" + param.name() + "> " + param.description());

		return helpLines.toArray(new String[]{});
	}

	private String getUsage(Method m)
	{
		StringBuilder builder = new StringBuilder();
		for (Annotation[] anl : m.getParameterAnnotations())
		{
			for (Annotation an : anl)
			{
				if (an.annotationType().equals(ChoobParam.class))
				{
					ChoobParam cp = (ChoobParam)an;
					builder.append("<");
					builder.append(cp.name());
					builder.append(">");
					builder.append(" (");
					builder.append(cp.description());
					builder.append(") ");
				}
			}
		}
		return builder.toString();
	}


}
