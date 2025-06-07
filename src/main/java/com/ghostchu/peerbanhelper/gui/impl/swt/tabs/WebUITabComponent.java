package com.ghostchu.peerbanhelper.gui.impl.swt.tabs;

import com.ghostchu.peerbanhelper.text.Lang;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

/**
 * WebUI 标签页组件
 */
public class WebUITabComponent implements TabComponent {
    private Browser browser;
    private String lastUrl = "about:blank";
    private Composite webUIComposite;

    @Override
    public TabItem createTab(TabFolder tabFolder) {
        TabItem webUITab = new TabItem(tabFolder, SWT.NONE);
        webUITab.setText(getTabName());

        webUIComposite = new Composite(tabFolder, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.horizontalSpacing = 0;
        layout.verticalSpacing = 0;
        webUIComposite.setLayout(layout);
        webUIComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createBrowser();

        webUITab.setControl(webUIComposite);

        return webUITab;
    }

    @Override
    public String getTabName() {
        return tlUI(Lang.GUI_TABBED_WEBUI);
    }

    /**
     * 隐藏窗口
     */
    @Override
    public void windowHide() {
        disposeBrowser();
    }

    /**
     * 显示窗口
     */
    @Override
    public void windowShow() {
        createBrowser();
    }

    public Browser getBrowser() {
        return browser;
    }

    private void disposeBrowser() {
        if (browser != null && !browser.isDisposed()) {
            browser.dispose();
            browser = null;
        }
    }

    private void createBrowser() {
        if (browser == null || browser.isDisposed()) {
            browser = new Browser(webUIComposite, SWT.NONE);
            browser.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            navigate("about:blank");
            webUIComposite.layout(true);
            browser.layout(true);
        }
    }

    public void navigate(String url) {
        this.lastUrl = url;
        if (browser != null && !browser.isDisposed()) {
            Display.getDefault().asyncExec(() -> browser.setUrl(lastUrl));
        }
    }
}
