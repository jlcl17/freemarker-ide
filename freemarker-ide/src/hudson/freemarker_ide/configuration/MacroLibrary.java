package hudson.freemarker_ide.configuration;

import hudson.freemarker_ide.Plugin;
import hudson.freemarker_ide.model.LibraryMacroDirective;
import hudson.freemarker_ide.model.MacroDirective;
import hudson.freemarker_ide.util.StringUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * @author <a href="mailto:joe@binamics.com">Joe Hudson</a>
 */
public class MacroLibrary {

	public static final String TYPE_FILE = "file";
	public static final String TYPE_JAR_ENTRY = "jarEntry";
	
	private long lastUpdatedTime;
	private IFile file;
	private String content;
	private String path;
	private String namespace;
	private String type;
	private MacroDirective[] macros;

	public MacroLibrary (String namespace, IFile file) throws IOException, CoreException {
		this.namespace = namespace;
		this.file = file;
		this.content = StringUtil.getStringFromStream(file.getContents(true));
		this.type = TYPE_FILE;
	}

	public MacroLibrary (String namespace, InputStream is, String path, String type) throws IOException {
		this.namespace = namespace;
		this.content = StringUtil.getStringFromStream(is);
		this.type = type;
		this.path = path;
		if (null == this.type) this.type = TYPE_FILE;
	}

	public synchronized MacroDirective[] getMacros() {
		if (null == macros
				|| isStale()) {
				load();
		}
		return macros;
	}

	public boolean isStale () {
		return (null != file && file.getModificationStamp() > lastUpdatedTime);
	}

	public IFile getFile () {
		return file;
	}

	private void load () {
		try {
			List macros = new ArrayList();
			String search = "#macro ";
			int index = content.indexOf(search);
			int startIndex = index;
			char startChar = content.charAt(index-1);
			char endChar;
			if (startChar == '[') endChar = ']';
			else endChar = '>';
			while (startIndex > 0) {
				int stackCount = 0;
				boolean inString = false;
				// find the end
				int endIndex = Integer.MIN_VALUE;
				boolean escape = false;
				while (content.length() > index && index >= 0) {
					boolean doEscape = false;
					char c = content.charAt(index++);
					if (!escape) {
						if (c == '\"') inString = !inString;
						else if (c == '\\' && inString) doEscape = true;
						else if (c == startChar) stackCount ++;
						else if (c == endChar) {
							if (stackCount > 0) stackCount --;
							else {
								endIndex = index-1;
								break;
							}
						}
					}
					escape = doEscape;
				}
				if (endIndex > 0) {
					String sub = content.substring(startIndex, endIndex);
					MacroDirective macroDirective = 
						new LibraryMacroDirective(namespace, sub, startIndex-1, endIndex-index+2);
					macros.add(macroDirective);
					index = content.indexOf(startChar + search, endIndex);
					if (index >= 0) index++;
					startIndex = index;
					endIndex = Integer.MIN_VALUE;
				}
				else {
					break;
				}
			}
			this.macros = (MacroDirective[]) macros.toArray(
					new MacroDirective[macros.size()]);
			if (null != file)
				this.lastUpdatedTime = file.getModificationStamp();
		}
		catch (Exception e) {
			macros = new MacroDirective[0];
			Plugin.log(e);
		}
	}

	public String getNamespace() {
		return namespace;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String toXML () {
		StringBuffer sb = new StringBuffer();
		sb.append("<entry namespace=\"" + getNamespace() + "\" ");
		sb.append("path=\"" + getPath() + "\" ");
		if (null != file) {
			sb.append("project=\"" + file.getProject().getName() + "\" ");
		}
		sb.append("type=\"" + getType() + "\"/>");
		return sb.toString();
	}

	public String getPath () {
		if (null != file)
			return file.getProjectRelativePath().toString();
		else
			return path;
	}

	public static MacroLibrary fromXML (IProject project, Element node, ClassLoader classLoader) throws CoreException, IOException {
        String namespace = node.getAttribute("namespace");
        String path = node.getAttribute("path");
        String projectName = node.getAttribute("project");
        String type = node.getAttribute("type");
        if (null == type || type.length() == 0 || type.equals(TYPE_FILE)) {
        	if (null != projectName && projectName.length() > 0)
        		project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        	IFile file = project.getFile(new Path(path));
        	if (null == file || !file.exists()) return null;
        	else return new MacroLibrary(namespace, file);
        }
        else if (type.equals(TYPE_JAR_ENTRY)) {
        	InputStream is = classLoader.getResourceAsStream(path);
        	if (null != is) {
        		return new MacroLibrary(namespace, is, path, TYPE_JAR_ENTRY);
        	}
        	else return null;
        }
        else return null;
	}
}