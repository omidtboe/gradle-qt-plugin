package omidtboe.qt.internal;

import omidtboe.qt.UiSourceSet;
import org.gradle.language.nativeplatform.internal.AbstractHeaderExportingSourceSet;

public class DefaultUiSourceSet extends AbstractHeaderExportingSourceSet implements UiSourceSet {
    @Override
    protected String getLanguageName() {
        return "QtUi";
    }
}
