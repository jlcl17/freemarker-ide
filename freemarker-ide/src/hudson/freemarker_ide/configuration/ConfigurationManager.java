package hudson.freemarker_ide.configuration;

import hudson.freemarker_ide.Plugin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JarEntryFile;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.swt.widgets.Shell;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author <a href="mailto:joe@binamics.com">Joe Hudson</a>
 */
public class ConfigurationManager {

	private static final Map instances = new HashMap();

	private IProject project;
	private ProjectClassLoader projectClassLoader;
	private Map contextValues = new HashMap();
	private Map macroLibrary = new HashMap();
	private MacroLibrary[] macroLibraryArr;

	private ConfigurationManager () {}

	public synchronized static final ConfigurationManager getInstance (IProject project) {
		ConfigurationManager configuration =
			(ConfigurationManager) instances.get(project.getName());
		if (null == configuration) {
			configuration = new ConfigurationManager();
			configuration.project = project;
			configuration.reload();
			instances.put(project.getName(), configuration);
		}
		return configuration;
	}

	public MacroLibrary[] getMacroLibraries () {
		return macroLibraryArr;
	}

	public void associateMappingLibraries (List libraries, Shell shell) {
		for (Iterator i=libraries.iterator(); i.hasNext(); ) {
			Object obj = i.next();
			if (obj instanceof IFile) {
				IFile file = (IFile) obj;
				String namespace = file.getName();
				int index = namespace.indexOf(".");
				if (index >= 0) namespace = namespace.substring(0, index);
				InputDialog inputDialog = new InputDialog(
						shell, "Choose Macro Library Namespace",
						"Please choose the namespace for '" + file.getName() + "'",
						namespace, null);
				int rtn = inputDialog.open();
				if (rtn == IDialogConstants.OK_ID) {
					namespace = inputDialog.getValue();
					try {
						this.macroLibrary.put(namespace, new MacroLibrary(namespace, file));
					}
					catch (CoreException e) {
						Plugin.error(e);
					}
					catch (IOException e) {
						Plugin.error(e);
					}
				}
			}
			else if (obj instanceof JarEntryFile) {
				JarEntryFile jef = (JarEntryFile) obj;
				String namespace = jef.getName();
				int index = namespace.indexOf(".");
				if (index >= 0) namespace = namespace.substring(0, index);
				InputDialog inputDialog = new InputDialog(
						shell, "Choose Macro Library Namespace",
						"Please choose the namespace for '" + jef.getName() + "'",
						namespace, null);
				int rtn = inputDialog.open();
				if (rtn == IDialogConstants.OK_ID) {
					namespace = inputDialog.getValue();
					try {
						InputStream is = getProjectClassLoader().getResourceAsStream(jef.getFullPath().toString());
						if (null != is) {
							this.macroLibrary.put(namespace, new MacroLibrary(namespace, is, jef.getFullPath().toString(), MacroLibrary.TYPE_JAR_ENTRY));
						}
						else {
							// FIXME: add error dialog here
						}
					}
					catch (CoreException e) {
						Plugin.error(e);
					}
					catch (IOException e) {
						Plugin.error(e);
					}
				}
			}
		}
		save();
	}
	
	public MacroLibrary getMacroLibrary (String namespace) {
		return (MacroLibrary) macroLibrary.get(namespace);
	}

	private void writeMacroLibrary(StringBuffer sb) {
		for (Iterator i=macroLibrary.values().iterator(); i.hasNext(); ) {
			MacroLibrary macroLibrary = (MacroLibrary) i.next();
			sb.append("\t\t" + macroLibrary.toXML() + "\n");
		}
	}
	
	private Map loadMacroTemplates (Element element) {
		Map map = new HashMap();
        try {
            NodeList nl = element
                    .getElementsByTagName("entry");
            for (int i = 0; i < nl.getLength(); i++) {
                try {
                    Node n = nl.item(i);
                    MacroLibrary macroLibrary = MacroLibrary.fromXML(project, (Element) n, getProjectClassLoader());
                    if (null != macroLibrary) {
                    	map.put(macroLibrary.getNamespace(), macroLibrary);
                    }
                    
                } catch (Exception e) {
                	Plugin.log(e);
                }
            }
	    }
        catch (Exception e) {
        	Plugin.log(e);
        }
        return map;
	}

	private IFile getFile (String path) {
		return project.getFile(path);
	}

	private String getPath (IFile file) {
		return file.getProjectRelativePath().toString();
	}

	private Map loadContextValues (Element element) {
		Map map = new HashMap();
        try {
            NodeList nl = element
                    .getElementsByTagName("resource");
            for (int i = 0; i < nl.getLength(); i++) {
                try {
                    Node n = nl.item(i);
                    String path = ((Element) n).getAttribute("path");
                    List contextValues = new ArrayList();
                    NodeList nl2 = ((Element) n)
                            .getElementsByTagName("value");
                    for (int j = 0; j < nl2.getLength(); j++) {
                        Node n2 = nl2.item(j);
                        String key = ((Element) n2).getAttribute("key");
                        Class value = getClass(((Element) n2)
                                .getAttribute("object-class"));
                        String singularName = ((Element) n2)
                                .getAttribute("item-class");
                        Class singularClass = null;
                        if (null != singularName && singularName.trim().length()>0)
                            singularClass = getClass(singularName);
                        contextValues.add(new ContextValue(key, value,
                                singularClass));
                    }
                    map.put(path,
                            contextValues
                                    .toArray(new ContextValue[contextValues
                                            .size()]));
                } catch (Exception e) {
                	Plugin.log(e);
                }
            }
	    }
        catch (Exception e) {
        	Plugin.log(e);
        }
        return map;
	}

    public synchronized Class getClass(String className)
    throws JavaModelException, ClassNotFoundException {
    	return getProjectClassLoader().loadClass(className);
    }

    public synchronized ClassLoader getProjectClassLoader() throws JavaModelException {
    	if (null == this.projectClassLoader)
    		this.projectClassLoader = new ProjectClassLoader(JavaCore.create(project));
    	return this.projectClassLoader;
    }
    
    private void save() {
        StringBuffer sb = new StringBuffer();
        sb.append("<config>\n");
        sb.append("\t<context-values>\n");
        writeContextValues(sb);
        sb.append("\t</context-values>\n");
        sb.append("\t<macro-library>\n");
        writeMacroLibrary(sb);
        sb.append("\t</macro-library>\n");
        sb.append("</config>");
        IFile file = project.getFile(".freemarker-ide.xml");
        try {
            if (file.exists())
                file.setContents(new ByteArrayInputStream(sb.toString()
                        .getBytes()), true, true, null);
            else
                file.create(new ByteArrayInputStream(sb.toString().getBytes()),
                        true, null);
        } catch (Exception e) {
            Plugin.error(e);
        }
        reload();
    }

    public void reload() {
    	this.projectClassLoader = null;
        IFile file = project.getFile(".freemarker-ide.xml");
        if (file.exists()) {
        	try { file.refreshLocal(1, null); } catch (CoreException e) {}
            Map map = new HashMap();
            try {
                Document document = DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder().parse(file.getContents());
                NodeList nl = document.getDocumentElement()
                        .getElementsByTagName("context-values");
                if (nl.getLength() > 0)
                    this.contextValues = loadContextValues((Element) nl.item(0));
                else
                    this.contextValues = new HashMap();
                nl = document.getDocumentElement()
                	.getElementsByTagName("macro-library");
                List libraries = new ArrayList();
                if (nl.getLength() > 0) {
                	this.macroLibrary = loadMacroTemplates((Element) nl.item(0));
                	for (Iterator i=macroLibrary.values().iterator(); i.hasNext(); ) {
                		libraries.add(i.next());
                	}
                }
                else
                	this.macroLibrary = new HashMap();
                macroLibraryArr = (MacroLibrary[]) libraries.toArray(new MacroLibrary[libraries.size()]);
            } catch (Exception e) {
            	Plugin.error(e);
            }
        }
    }

    private void writeContextValues(StringBuffer sb) {
        for (Iterator i = contextValues.entrySet().iterator(); i.hasNext();) {
            Map.Entry entry = (Map.Entry) i.next();
            String fileName = (String) entry.getKey();
            ContextValue[] values = (ContextValue[]) entry.getValue();
            if (null != values && values.length > 0) {
                sb.append("\t\t<resource path=\"" + fileName + "\">\n");
                for (int j = 0; j < values.length; j++) {
                    sb.append("\t\t\t<value key=\"" + values[j].name
                            + "\" object-class=\"" + values[j].objClass.getName()
                            + "\"");
                    if (null != values[j].singularClass)
                        sb.append(" item-class=\""
                                + values[j].singularClass.getName() + "\"");
                    sb.append("/>\n");
                }
                sb.append("\t\t</resource>\n");
            }
        }
    }
    
    public ContextValue[] getContextValues(IResource resource, boolean recurse) {
        Map newValues = new HashMap();
        addRootContextValues(resource, newValues, recurse);
        return (ContextValue[]) newValues.values().toArray(new ContextValue[newValues.size()]);
    }
    
    private void addRootContextValues(IResource resource, Map newValues, boolean recurse) {
        String key = null;
        if (null != resource.getParent()) {
            key = resource.getProjectRelativePath().toString();
            if (recurse) addRootContextValues(resource.getParent(), newValues, true);
        }
        else key = "";
        if (null != resource.getProject()) {
	        ContextValue[] values = (ContextValue[]) contextValues.get(key);
	        if (null != values) {
	            for (int i=0; i<values.length; i++) {
	                newValues.put(values[i].name, values[i]);
	            }
	        }
        }
    }

    public ContextValue getContextValue(String name, IResource resource, boolean recurse) {
        ContextValue[] values = getContextValues(resource, recurse);
        for (int i = 0; i < values.length; i++) {
            if (values[i].name.equals(name))
                return values[i];
        }
        return null;
    }

    public void addContextValue(ContextValue contextValue, IResource resource) {
        ContextValue[] contextValues = getContextValues(resource, false);
        boolean found = false;
        for (int i = 0; i < contextValues.length; i++) {
            if (contextValues[i].name.equals(contextValue.name)) {
                found = true;
                contextValues[i] = contextValue;
                this.contextValues.put(
                		resource.getProjectRelativePath().toString(),
						contextValues);
                break;
            }
        }
        if (!found) {
            ContextValue[] newContextValues = new ContextValue[contextValues.length + 1];
            int index = 0;
            while (index < contextValues.length) {
                newContextValues[index] = contextValues[index++];
            }
            newContextValues[index] = contextValue;
            this.contextValues.put(resource.getProjectRelativePath().toString(), newContextValues);
        }
        save();
    }

    public void updateContextValue(ContextValue contextValue, IFile file) {
        addContextValue(contextValue, file);
    }

    public void removeContextValue(String name, IResource resource) {
        ContextValue[] values = getContextValues(resource, false);
        int index = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i].name.equals(name)) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            ContextValue[] newValues = new ContextValue[values.length - 1];
            int j = 0;
            for (int i = 0; i < values.length; i++) {
                if (i != index)
                    newValues[j++] = values[i];
            }
            this.contextValues.put(resource.getProjectRelativePath().toString(), newValues);
            save();
        }
    }
}