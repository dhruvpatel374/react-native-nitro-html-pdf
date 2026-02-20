import Foundation
import UIKit
import WebKit
import NitroModules

class NitroHtmlPdf: HybridNitroHtmlPdfSpec {
    
    public func generatePdf(options: PdfOptions) throws -> Promise<PdfResult> {
        return Promise.async { [weak self] in
            return try await self?.createPdf(options: options) ?? PdfResult(filePath: "", success: false, error: "Failed to create PDF")
        }
    }
    
    private func createPdf(options: PdfOptions) async throws -> PdfResult {
        return try await withCheckedThrowingContinuation { continuation in
            DispatchQueue.main.async {
                let pageSizeString = options.pageSize?.stringValue ?? "A4"
                let pageSize = self.getPageSize(pageSizeString, width: options.width, height: options.height)
                let headerHeight = CGFloat(options.headerHeight ?? 0)
                let footerHeight = CGFloat(options.footerHeight ?? 0)
                
                // Create offscreen container view
                let containerView = UIView(frame: CGRect(x: -10000, y: -10000, width: pageSize.width, height: 10000))
                containerView.alpha = 0
                UIApplication.shared.windows.first?.addSubview(containerView)
                
                // Create header/footer webviews
                var headerWebView: WKWebView?
                var footerWebView: WKWebView?
                
                if let header = options.header, headerHeight > 0 {
                    let config = WKWebViewConfiguration()
                    config.preferences.javaScriptEnabled = false
                    let hwv = WKWebView(frame: CGRect(x: 0, y: 0, width: pageSize.width, height: headerHeight), configuration: config)
                    hwv.scrollView.contentInset = .zero
                    hwv.scrollView.contentInsetAdjustmentBehavior = .never
                    let wrappedHeader = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'><style>*{margin:0!important;padding:0!important;box-sizing:border-box;}html,body{margin:0!important;padding:0!important;width:100%;height:100%;overflow:hidden;}</style></head><body>\(header)</body></html>"
                    hwv.loadHTMLString(wrappedHeader, baseURL: nil)
                    containerView.addSubview(hwv)
                    headerWebView = hwv
                }
                
                if let footer = options.footer, footerHeight > 0 {
                    let config = WKWebViewConfiguration()
                    config.preferences.javaScriptEnabled = false
                    let fwv = WKWebView(frame: CGRect(x: 0, y: 0, width: pageSize.width, height: footerHeight), configuration: config)
                    fwv.scrollView.contentInset = .zero
                    fwv.scrollView.contentInsetAdjustmentBehavior = .never
                    let wrappedFooter = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'><style>*{margin:0!important;padding:0!important;box-sizing:border-box;}html,body{margin:0!important;padding:0!important;width:100%;height:100%;overflow:hidden;}</style></head><body>\(footer)</body></html>"
                    fwv.loadHTMLString(wrappedFooter, baseURL: nil)
                    containerView.addSubview(fwv)
                    footerWebView = fwv
                }
                
                // Create main content webview
                let webView = WKWebView(frame: CGRect(x: 0, y: 0, width: pageSize.width, height: 842))
                webView.loadHTMLString(options.html, baseURL: nil)
                containerView.addSubview(webView)
                
                // Wait for all to load
                DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                    self.renderPdf(webView: webView, headerWebView: headerWebView, footerWebView: footerWebView, options: options, continuation: continuation)
                    containerView.removeFromSuperview()
                }
            }
        }
    }
    
    private func renderPdf(webView: WKWebView, headerWebView: WKWebView?, footerWebView: WKWebView?, options: PdfOptions, continuation: CheckedContinuation<PdfResult, Error>) {
        let pageSizeString = options.pageSize?.stringValue ?? "A4"
        let pageSize = getPageSize(pageSizeString, width: options.width, height: options.height)
        
        let headerHeight = CGFloat(options.headerHeight ?? 0)
        let footerHeight = CGFloat(options.footerHeight ?? 0)
        let margins = UIEdgeInsets(
            top: CGFloat(options.marginTop ?? 0) + headerHeight,
            left: CGFloat(options.marginLeft ?? 0),
            bottom: CGFloat(options.marginBottom ?? 0) + footerHeight + (options.showPageNumbers ?? false ? 20 : 0),
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
        
        let directory = options.directory ?? NSTemporaryDirectory()
        let filePath = (directory as NSString).appendingPathComponent(options.fileName)
        
        do {
            try pdfData.write(toFile: filePath, options: .atomic)
            continuation.resume(returning: PdfResult(filePath: filePath, success: true, error: nil))
        } catch {
            continuation.resume(returning: PdfResult(filePath: "", success: false, error: error.localizedDescription))
        }
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
        
        super.drawPage(at: pageIndex, in: printableRect)
        
        if let headerWebView = headerWebView, customHeaderHeight > 0 {
            context.saveGState()
            headerWebView.layer.render(in: context)
            context.restoreGState()
        }
        
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
            footerWebView.layer.render(in: context)
            context.restoreGState()
        }
    }
}
