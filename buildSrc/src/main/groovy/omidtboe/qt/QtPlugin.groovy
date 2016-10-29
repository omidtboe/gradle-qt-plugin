package omidtboe.qt

import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.nativeplatform.tasks.LinkExecutable

import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.TypeBuilder
import org.gradle.platform.base.ComponentType

import org.apache.commons.lang.StringUtils
import org.gradle.model.Defaults
import org.gradle.model.Model
import org.gradle.model.ModelMap
import org.gradle.model.Mutate
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.language.cpp.CppSourceSet
import org.gradle.language.cpp.tasks.CppCompile


import omidtboe.qt.UiSourceSet
import omidtboe.qt.internal.DefaultUiSourceSet
import omidtboe.qt.QtSettings

class QtPlugin extends RuleSource {
	private boolean isValidModule(String module) {
		// TODO Add other modules
		return module in ['QtCore', 'QtGui', 'QtWidgets']
	}

	@Model
	void qtSettings(QtSettings qtSettings) {}

	@Defaults
	void setDefaultQtSettings(QtSettings settings) {
		settings.setVersion("Qt5")
		settings.headerDir "/usr/include/qt5/"
		settings.libDir "/lib64/"
		settings.setModules(['QtCore', 'QtGui'])
	}

	@Defaults
	void createQtMocTask(@Path("binaries") ModelMap<BinarySpec> binaries, final @Path("buildDir") File buildDir, final @Path("qtSettings") QtSettings settings) {
		def moc = 'moc-qt4'
		if (settings.version.equalsIgnoreCase('qt5')) {
			moc = 'moc-qt5'
		}
		binaries.beforeEach { binary ->
			binary.inputs.withType(CppSourceSet.class) { sourceSet ->
				def taskName = "${binary.getNamingScheme().getTaskName('generateMoc')}${StringUtils.capitalize(sourceSet.parentName)}${StringUtils.capitalize(sourceSet.name)}"
				def destDir = new File(buildDir, "generated/${sourceSet.parentName}/moc")

				// Create moc task
				binary.tasks.create(taskName, MocCompileTask) { task ->
					task.moc = moc
					// Add header files to moc task
					task.source = sourceSet.source.filter { x -> x.isFile() && x.getName().matches(".+?\\.h\\w*?") }
					task.source += sourceSet.exportedHeaders.filter { x -> x.isFile() && x.getName().matches(".+?\\.h\\w*?") }
					task.destinationDir = destDir
					task.description = "Generates Qt moc source files '${sourceSet.parentName}:${sourceSet.name}' for ${binary.displayName}"
				}

				def compileTaskName = "${binary.getNamingScheme().getTaskName('compileMoc')}${StringUtils.capitalize(sourceSet.parentName)}${StringUtils.capitalize(sourceSet.name)}"

				// Add destination of generated moc_*.cpp to sourceset
				sourceSet.source.srcDir(destDir)

				// Set dependency so moc cpp files are compiled first
				binary.tasks.withType(CppCompile.class) { compileTask ->
					compileTask.dependsOn(taskName)
				}
			}
		}
	}

	@ComponentType
	void registerLanguage(TypeBuilder<UiSourceSet> builder) {
		builder.defaultImplementation(DefaultUiSourceSet.class);
	}

	@Mutate
	void createQtUicTasks(@Path("binaries") ModelMap<BinarySpec> binaries, final @Path("buildDir") File buildDir, QtSettings settings) {
		def uic = 'uic-qt4'
		if (settings.version.equalsIgnoreCase('qt5')) {
			uic = 'uic-qt5'
		}
		binaries.beforeEach { binary ->
			binary.inputs.withType(UiSourceSet.class) { uiSourceSet ->
				def taskName = "${binary.getNamingScheme().getTaskName('generateUi')}${StringUtils.capitalize(uiSourceSet.parentName)}${StringUtils.capitalize(uiSourceSet.name)}Headers"
				def destDir = new File(buildDir, "generated/${uiSourceSet.parentName}/uic")

				// Create uic task
				binary.tasks.create(taskName, UiCompileTask) { task ->
					task.uic = uic
					task.source = uiSourceSet.source
					task.destinationDir = destDir
					task.description = "Generates Qt ui header files '${uiSourceSet.parentName}:${uiSourceSet.name}' for ${binary.displayName}"
				}

				// Set exported header directory
				uiSourceSet.exportedHeaders.srcDir(destDir)

				// Add exported headers to cpp source sets
				binary.inputs.withType(CppSourceSet.class) { cppSourceSet ->
					cppSourceSet.lib(uiSourceSet)
				}

				// Set dependency so ui headers are compiled first
				binary.tasks.withType(CppCompile.class) { compileTask ->
					compileTask.dependsOn(taskName)
				}
			}
		}
	}

	@Mutate
	void addQtDependencies(@Path("binaries") ModelMap<BinarySpec> binaries, QtSettings qtSettings) {
		// Filter out invalid qt modules
		def qtModules = []
		qtSettings.modules.each {
			mod ->
			if (isValidModule(mod))
			{
				qtModules += mod
			}
			else
			{
				println("Dropping invalid Qt module '${mod}'")
			}
		}

		binaries.beforeEach { binary ->
			// Add qt include paths to CppCompile tasks
			binary.tasks.withType(CppCompile.class) { compileTask ->
				compileTask.includes(qtSettings.headerDir)
				qtModules.each {
					mod -> compileTask.includes(new File(qtSettings.headerDir, mod))
				}
			}
		}

		binaries.withType(NativeExecutableBinarySpec) { binary ->
			binary.tasks.withType(LinkExecutable.class) { task ->
				qtSettings.modules.each {
					// TODO Handle Qt5 and Qt4
					mod -> task.lib(task.getProject().files("/usr/lib64/lib${mod}.so".replaceAll('Qt', 'Qt5')))
				}
			}

		}

		binaries.withType(SharedLibraryBinarySpec.class) { binary ->
			binary.tasks.withType(LinkSharedLibrary.class) { task ->
				qtSettings.modules.each {
					mod -> task.lib(task.getProject().files("/usr/lib64/lib${mod}.so".replaceAll('Qt', 'Qt5')))
				}
			}
		}
	}
}
