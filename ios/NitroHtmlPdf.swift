import Foundation
import UIKit
import WebKit
import NitroModules

class NitroHtmlPdf: HybridNitroHtmlPdfSpec {
    private let timeout: TimeInterval = 60
    private var activeDelegates: [WebViewDelegate] = []
    
    public func generatePdf(options: PdfOptions) throws -> Promise<PdfResult> {
        return Promise.async { [weak self] in
            guard let self = self else {
                return PdfResult(filePath: "", success: false, numberOfPages: nil, error: "Failed to create PDF")
            }
            
            if let error = self.validateOptions(options) {
                return PdfResult(filePath: "", success: false, numberOfPages: nil, error: error)
            }
            
            return try await withTimeout(seconds: self.timeout) {
                try await self.createPdf(options: options)
            }
        }
    }
    
    private func validateOptions(_ options: PdfOptions) -> String? {
        if options.html.isEmpty {
            return "HTML content cannot be empty"
        }
        
        if options.fileName.isEmpty {
            return "File name cannot be empty"
        }
        
        if !options.fileName.lowercased().hasSuffix(".pdf") {
            return "File name must end with .pdf"
        }
        
        let hasHeader = options.header != nil && !options.header!.isEmpty
        let hasFooter = options.footer != nil && !options.footer!.isEmpty
        
        if hasHeader && (options.headerHeight == nil || options.headerHeight! <= 0) {
            return "headerHeight must be provided and greater than 0 when using header"
        }
        
        if hasFooter && (options.footerHeight == nil || options.footerHeight! <= 0) {
            return "footerHeight must be provided and greater than 0 when using footer"
        }
        
        if let fontSize = options.pageNumberFontSize, fontSize <= 0 {
            return "pageNumberFontSize must be greater than 0"
        }
        
        return nil
    }
    
    private func withTimeout<T>(seconds: TimeInterval, operation: @escaping () async throws -> T) async throws -> T {
        try await withThrowingTaskGroup(of: T.self) { group in
            group.addTask {
                try await operation()
            }
            group.addTask {
                try await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
                throw TimeoutError()
            }
            let result = try await group.next()!
            group.cancelAll()
            return result
        }
    }
    
    private struct TimeoutError: Error {
        var localizedDescription: String { "PDF generation timeout after 60 seconds" }
    }
    
    private func createPdf(options: PdfOptions) async throws -> PdfResult {
        return try await withCheckedThrowingContinuation { continuation in
            DispatchQueue.main.async {
                let pageSizeString = options.pageSize?.stringValue ?? "A4"
                let pageSize = self.getPageSize(pageSizeString, width: options.width, height: options.height)
                let headerHeight = CGFloat(options.headerHeight ?? 0)
                let footerHeight = CGFloat(options.footerHeight ?? 0)
                
                let containerView = UIView(frame: CGRect(x: -10000, y: -10000, width: pageSize.width, height: 10000))
                containerView.alpha = 0
                UIApplication.shared.windows.first?.addSubview(containerView)
                
                var headerWebView: WKWebView?
                var footerWebView: WKWebView?
                var contentWebView: WKWebView?
                var loadedCount = 0
                var totalToLoad = 1
                
                if let header = options.header, headerHeight > 0 {
                    totalToLoad += 1
                    let hwv = WKWebView(frame: CGRect(x: 0, y: 0, width: pageSize.width, height: headerHeight))
                    hwv.scrollView.contentInset = .zero
                    hwv.scrollView.contentInsetAdjustmentBehavior = .never
                    let wrappedHeader = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'><style>*{margin:0!important;padding:0!important;box-sizing:border-box;}html,body{margin:0!important;padding:0!important;width:100%;height:100%;overflow:hidden;}svg{display:block;width:100%;height:100%;}</style></head><body>\(header)</body></html>"
                    let delegate = WebViewDelegate {
                        self.waitForWebViewReady(hwv) {
                            loadedCount += 1
                            if loadedCount == totalToLoad {
                                self.renderPdf(webView: contentWebView!, headerWebView: headerWebView, footerWebView: footerWebView, options: options, continuation: continuation)
                                containerView.removeFromSuperview()
                            }
                        }
                    }
                    self.activeDelegates.append(delegate)
                    hwv.navigationDelegate = delegate
                    hwv.loadHTMLString(wrappedHeader, baseURL: nil)
                    containerView.addSubview(hwv)
                    headerWebView = hwv
                }
                
                if let footer = options.footer, footerHeight > 0 {
                    totalToLoad += 1
                    let fwv = WKWebView(frame: CGRect(x: 0, y: 0, width: pageSize.width, height: footerHeight))
                    fwv.scrollView.contentInset = .zero
                    fwv.scrollView.contentInsetAdjustmentBehavior = .never
                    let wrappedFooter = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'><style>*{margin:0!important;padding:0!important;box-sizing:border-box;}html,body{margin:0!important;padding:0!important;width:100%;height:100%;overflow:hidden;}svg{display:block;width:100%;height:100%;}</style></head><body>\(footer)</body></html>"
                    let delegate = WebViewDelegate {
                        self.waitForWebViewReady(fwv) {
                            loadedCount += 1
                            if loadedCount == totalToLoad {
                                self.renderPdf(webView: contentWebView!, headerWebView: headerWebView, footerWebView: footerWebView, options: options, continuation: continuation)
                                containerView.removeFromSuperview()
                            }
                        }
                    }
                    self.activeDelegates.append(delegate)
                    fwv.navigationDelegate = delegate
                    fwv.loadHTMLString(wrappedFooter, baseURL: nil)
                    containerView.addSubview(fwv)
                    footerWebView = fwv
                }
                
                let webView = WKWebView(frame: CGRect(x: 0, y: 0, width: pageSize.width, height: 842))
                containerView.addSubview(webView)
                contentWebView = webView
                
                let delegate = WebViewDelegate {
                    self.waitForWebViewReady(contentWebView!) {
                        loadedCount += 1
                        if loadedCount == totalToLoad {
                            self.renderPdf(webView: contentWebView!, headerWebView: headerWebView, footerWebView: footerWebView, options: options, continuation: continuation)
                            containerView.removeFromSuperview()
                        }
                    }
                }
                self.activeDelegates.append(delegate)
                webView.navigationDelegate = delegate
                webView.loadHTMLString(options.html, baseURL: nil)
            }
        }
    }
    
    private func renderPdf(webView: WKWebView, headerWebView: WKWebView?, footerWebView: WKWebView?, options: PdfOptions, continuation: CheckedContinuation<PdfResult, Error>) {
        self.activeDelegates.removeAll()
        
        headerWebView?.layoutIfNeeded()
        footerWebView?.layoutIfNeeded()
        webView.layoutIfNeeded()
        
        let pageSizeString = options.pageSize?.stringValue ?? "A4"
        let pageSize = getPageSize(pageSizeString, width: options.width, height: options.height)
        
        let headerHeight = CGFloat(options.headerHeight ?? 0)
        let footerHeight = CGFloat(options.footerHeight ?? 0)
        let marginTop = CGFloat(options.marginTop ?? 0)
        let marginBottom = CGFloat(options.marginBottom ?? 0)
        let margins = UIEdgeInsets(
            top: marginTop + headerHeight,
            left: CGFloat(options.marginLeft ?? 0),
            bottom: marginBottom + footerHeight + (options.showPageNumbers ?? false ? 20 : 0),
            right: CGFloat(options.marginRight ?? 0)
        )
        
        let printFormatter = webView.viewPrintFormatter()
        let renderer = CustomPrintPageRenderer(
            headerWebView: headerWebView,
            footerWebView: footerWebView,
            headerHeight: headerHeight,
            footerHeight: footerHeight,
            pageSize: pageSize,
            showPageNumbers: options.showPageNumbers ?? false,
            pageNumberFormat: options.pageNumberFormat,
            pageNumberFontSize: CGFloat(options.pageNumberFontSize ?? 12)
        )
        renderer.addPrintFormatter(printFormatter, startingAtPageAt: 0)
        
        let printableRect = CGRect(
            x: margins.left,
            y: margins.top,
            width: pageSize.width - margins.left - margins.right,
            height: pageSize.height - margins.top - margins.bottom
        )
        let paperRect = CGRect(origin: .zero, size: pageSize)
        
        renderer.setValue(paperRect, forKey: "paperRect")
        renderer.setValue(printableRect, forKey: "printableRect")
        
        let pdfData = NSMutableData()
        UIGraphicsBeginPDFContextToData(pdfData, paperRect, nil)
        
        for i in 0..<renderer.numberOfPages {
            UIGraphicsBeginPDFPage()
            renderer.drawPage(at: i, in: paperRect)
        }
        
        UIGraphicsEndPDFContext()
        
        let numberOfPages = renderer.numberOfPages
        let directory = options.directory ?? NSTemporaryDirectory()
        let filePath = (directory as NSString).appendingPathComponent(options.fileName)
        
        do {
            try pdfData.write(toFile: filePath, options: .atomic)
            continuation.resume(returning: PdfResult(filePath: filePath, success: true, numberOfPages: Double(numberOfPages), error: nil))
        } catch {
            continuation.resume(returning: PdfResult(filePath: "", success: false, numberOfPages: nil, error: error.localizedDescription))
        }
    }
    
    private func waitForWebViewReady(_ webView: WKWebView, completion: @escaping () -> Void) {
        let js = """
        (function() {
            if (document.readyState !== 'complete') {
                return false;
            }
            const images = Array.from(document.images);
            return images.every(img => img.complete);
        })();
        """

        func check() {
            webView.evaluateJavaScript(js) { result, _ in
                if let isReady = result as? Bool, isReady {
                    completion()
                } else {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                        check()
                    }
                }
            }
        }

        check()
    }
    
    private func getPageSize(_ size: String, width: Double?, height: Double?) -> CGSize {
        if let w = width, let h = height {
            return CGSize(width: w, height: h)
        }
        
        switch size {
        case "A4": return CGSize(width: 595, height: 842)
        case "Letter": return CGSize(width: 612, height: 792)
        case "Legal": return CGSize(width: 612, height: 1008)
        case "A3": return CGSize(width: 842, height: 1191)
        case "A5": return CGSize(width: 420, height: 595)
        default: return CGSize(width: 595, height: 842)
        }
    }
}

class CustomPrintPageRenderer: UIPrintPageRenderer {
    private var headerWebView: WKWebView?
    private var footerWebView: WKWebView?
    private let customHeaderHeight: CGFloat
    private let customFooterHeight: CGFloat
    private let pageSize: CGSize
    private let showPageNumbers: Bool
    private let pageNumberFormat: String?
    private let pageNumberFontSize: CGFloat
    
    override var headerHeight: CGFloat {
        get { return 0 }
        set { }
    }
    
    override var footerHeight: CGFloat {
        get { return 0 }
        set { }
    }
    
    init(headerWebView: WKWebView?, footerWebView: WKWebView?, headerHeight: CGFloat, footerHeight: CGFloat, pageSize: CGSize, showPageNumbers: Bool, pageNumberFormat: String?, pageNumberFontSize: CGFloat) {
        self.headerWebView = headerWebView
        self.footerWebView = footerWebView
        self.customHeaderHeight = headerHeight
        self.customFooterHeight = footerHeight
        self.pageSize = pageSize
        self.showPageNumbers = showPageNumbers
        self.pageNumberFormat = pageNumberFormat
        self.pageNumberFontSize = pageNumberFontSize
        super.init()
    }
    
    override func drawPage(at pageIndex: Int, in printableRect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        
        if let headerWebView = headerWebView, customHeaderHeight > 0 {
            context.saveGState()
            context.translateBy(x: 0, y: 0)
            headerWebView.drawHierarchy(in: headerWebView.bounds, afterScreenUpdates: true)
            context.restoreGState()
        }
        
        super.drawPage(at: pageIndex, in: printableRect)
        
        if showPageNumbers {
            let currentPage = pageIndex + 1
            let totalPages = numberOfPages
            var pageText = pageNumberFormat ?? "Page {page} of {total}"
            pageText = pageText.replacingOccurrences(of: "{page}", with: "\(currentPage)")
            pageText = pageText.replacingOccurrences(of: "{total}", with: "\(totalPages)")
            
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: pageNumberFontSize),
                .foregroundColor: UIColor.black
            ]
            let textSize = pageText.size(withAttributes: attributes)
            let x = (pageSize.width - textSize.width) / 2
            let y = pageSize.height - customFooterHeight - textSize.height - 5
            
            pageText.draw(at: CGPoint(x: x, y: y), withAttributes: attributes)
        }
        
        if let footerWebView = footerWebView, customFooterHeight > 0 {
            context.saveGState()
            context.translateBy(x: 0, y: pageSize.height - customFooterHeight)
            footerWebView.drawHierarchy(in: footerWebView.bounds, afterScreenUpdates: true)
            context.restoreGState()
        }
    }
}

class WebViewDelegate: NSObject, WKNavigationDelegate {
    private let onFinish: () -> Void
    private var hasFinished = false
    
    init(onFinish: @escaping () -> Void) {
        self.onFinish = onFinish
    }
    
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        guard !hasFinished else { return }
        hasFinished = true
        onFinish()
    }
    
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        guard !hasFinished else { return }
        hasFinished = true
        onFinish()
    }
}