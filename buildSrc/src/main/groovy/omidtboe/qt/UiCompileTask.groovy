package omidtboe.qt

import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction

class UiCompileTask extends SourceTask {
	@OutputDirectory
	File destinationDir

	String uic

	@TaskAction
	void uiCompile() {
		source.filter{ x -> x.isFile() }.each { 
			def baseName = it.name - ".ui"
			def headerFile = "${destinationDir}/ui_${baseName}.h"
			def process = "${uic} -o ${headerFile} -g cpp ${it.path}".execute()
			process.waitFor()
			if (process.exitValue()) {
				println process.err.text
			}
		}
         }
}
