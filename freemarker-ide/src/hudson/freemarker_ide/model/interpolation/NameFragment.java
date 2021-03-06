package hudson.freemarker_ide.model.interpolation;

import hudson.freemarker_ide.configuration.ConfigurationManager;
import hudson.freemarker_ide.configuration.ContextValue;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.source.ISourceViewer;

/**
 * @author <a href="mailto:joe@binamics.com">Joe Hudson</a>
 */
public class NameFragment extends AbstractFragment {

	public NameFragment(int offset, String content) {
		super(offset, content);
	}

	public ICompletionProposal[] getCompletionProposals (int subOffset, int offset, Class parentClass,
			List fragments, ISourceViewer sourceViewer, Map context, IResource file, IProject project) {
		if (isStartFragment()) {
			// pull from context
			String prefix = getContent().substring(0, subOffset);
			List proposals = new ArrayList();
			for (Iterator i=context.keySet().iterator(); i.hasNext(); ) {
				String key = (String) i.next();
				if (key.startsWith(prefix)) proposals.add(getCompletionProposal(
						offset, subOffset, key, getContent()));
			}
			return completionProposals(proposals);
		}
		else {
			if (null == parentClass) return null;
			return getMethodCompletionProposals (subOffset, offset, parentClass, file);
		}
	}

	private Class returnClass;
	public Class getReturnClass (Class parentClass, List fragments, Map context, IResource resource, IProject project){
		if (null == returnClass) {
			String content = getContent();
			if (isStartFragment()) {
				returnClass = (Class) context.get(content);
			}
			else {
				if (null == parentClass) {
					returnClass = Object.class;
				}
				else {
					content = Character.toUpperCase(content.charAt(1)) + content.substring(2, getContent().length());
					String getcontent = "get" + content;
					for (int i=0; i<parentClass.getMethods().length; i++) {
						Method m = parentClass.getMethods()[i];
						if (m.getName().equals(content) || m.getName().equals(getcontent)) {
							returnClass = m.getReturnType();
							break;
						}
					}
				}
			}
		}
		return returnClass;
	}

	private Class singulaReturnClass;
	public Class getSingularReturnClass(Class parentClass, List fragments, Map context, IResource resource, IProject project) {
		if (null == singulaReturnClass) {
			String content = getContent();
			if (isStartFragment()) {
				ContextValue contextValue = ConfigurationManager.getInstance(project).getContextValue(content, resource, true);
				if (null == contextValue || null == contextValue.singularClass)
					singulaReturnClass = Object.class;
				else
					singulaReturnClass = contextValue.singularClass;
			}
			else {
				if (null == parentClass) {
					singulaReturnClass = Object.class;
				}
				else {
					content = Character.toUpperCase(content.charAt(1)) + content.substring(2, getContent().length());
					String getcontent = "get" + content;
					for (int i=0; i<parentClass.getMethods().length; i++) {
						Method m = parentClass.getMethods()[i];
						if (m.getName().equals(content) || m.getName().equals(getcontent)) {
							Type type = m.getGenericReturnType();
							if (type instanceof ParameterizedType) {
								ParameterizedType pType = (ParameterizedType) type;
								if (pType.getActualTypeArguments().length > 0) {
									singulaReturnClass = (Class) pType.getActualTypeArguments()[0];
									break;
								}
							}
							singulaReturnClass = Object.class;
							break;
						}
					}
				}
			}
		}
		return singulaReturnClass;
	}

	public boolean isStartFragment () {
		return !getContent().startsWith(".");
	}

	public static final String[] invalidMethods = {
		"clone", "equals", "finalize", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait"};
	public ICompletionProposal[] getMethodCompletionProposals (int subOffset, int offset, Class parentClass, IResource file) {
		if (instanceOf(parentClass, String.class)
				|| instanceOf(parentClass, Number.class)
				|| instanceOf(parentClass, Date.class)
				|| instanceOf(parentClass, Collection.class)
				|| instanceOf(parentClass, List.class)
				|| instanceOf(parentClass, Map.class))
			return null;
		String prefix = getContent().substring(1, subOffset);
		List proposals = new ArrayList();
		String pUpper = prefix.toUpperCase();
		try {
			BeanInfo bi = Introspector.getBeanInfo(parentClass);
			PropertyDescriptor[] pds = bi.getPropertyDescriptors();
			for (int i=0; i<pds.length; i++) {
				PropertyDescriptor pd = pds[i];
				String propertyName = pd.getName();
				if (!propertyName.equals("class") && propertyName.toUpperCase().startsWith(pUpper)) {
					proposals.add(new CompletionProposal(
							propertyName,
							offset - subOffset + 1,
							getContent().length()-1,
							propertyName.length(),
							null, propertyName + " - " + pd.getReadMethod().getReturnType().getName(), null, null));
				}
			}
			for (int i=0; i<parentClass.getMethods().length; i++) {
				Method m = parentClass.getMethods()[i];
				String mName = m.getName();
				if (m.getParameterTypes().length > 0 && mName.startsWith("get") && mName.toUpperCase().startsWith(pUpper)) {
					StringBuffer display = new StringBuffer();
					display.append(mName);
					display.append("(");
					for (int j=0; j<m.getParameterTypes().length; j++) {
						if (j > 0) display.append(", ");
						display.append(m.getParameterTypes()[j].getName());
					}
					display.append(")");
					String actual = mName + "()";
					int tLength = actual.length();
					if (m.getParameterTypes().length > 0) tLength--;
					proposals.add(new CompletionProposal(actual,
							offset - subOffset + 1, getContent().length()-1, tLength,
							null, display.toString() + " - " + m.getReturnType().getName(), null, null));
				}
			}
			return completionProposals(proposals);
		}
		catch (IntrospectionException e) {
			return null;
		}
	}
}