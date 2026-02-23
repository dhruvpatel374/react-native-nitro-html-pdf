import { useState } from 'react';
import {
  Text,
  View,
  StyleSheet,
  Button,
  ScrollView,
  Alert,
  Share,
  Linking,
  TouchableOpacity,
  Image,
  Dimensions,
} from 'react-native';
import { generatePdf } from 'react-native-nitro-html-pdf';
import type { PdfResult } from 'react-native-nitro-html-pdf';
import { HEADER_HTML, FOOTER_HTML } from './invoiceTemplate';

const { width: screenWidth } = Dimensions.get('window');

const formatDate = (dateString: string) => {
  const date = new Date(dateString);
  return date.toLocaleDateString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
};

const generateInvoiceHTML = () => {
  const billData = {
    billNumber: 'INV-2024-001',
    createdAt: new Date().toISOString(),
    technicianId: {
      companyName: 'Fiixxo Services',
      address: '123 Business Street, Mumbai, Maharashtra 400001',
      phoneNumber: '+91 98765 43210',
    },
    customerId: {
      fullName: 'John Doe',
      fullAddress: '456 Customer Road',
      area: 'Andheri West, Mumbai',
      contactNumber: '+91 98765 12345',
    },
    items: [
      {
        partName: 'AC Repair Service',
        pricePerUnit: 1500,
        quantity: 1,
        totalPrice: 1500,
      },
      {
        partName: 'Gas Refill',
        pricePerUnit: 2500,
        quantity: 1,
        totalPrice: 2500,
      },
      {
        partName: 'Spare Parts',
        pricePerUnit: 800,
        quantity: 2,
        totalPrice: 1600,
      },
      {
        partName: 'Compressor Replacement',
        pricePerUnit: 3500,
        quantity: 1,
        totalPrice: 3500,
      },
      {
        partName: 'Condenser Coil',
        pricePerUnit: 1200,
        quantity: 1,
        totalPrice: 1200,
      },
      {
        partName: 'Thermostat',
        pricePerUnit: 450,
        quantity: 2,
        totalPrice: 900,
      },
      {
        partName: 'Fan Motor',
        pricePerUnit: 2200,
        quantity: 1,
        totalPrice: 2200,
      },
      {
        partName: 'Capacitor',
        pricePerUnit: 350,
        quantity: 3,
        totalPrice: 1050,
      },
      {
        partName: 'Refrigerant R32',
        pricePerUnit: 1800,
        quantity: 2,
        totalPrice: 3600,
      },
      {
        partName: 'Copper Piping',
        pricePerUnit: 600,
        quantity: 5,
        totalPrice: 3000,
      },
      {
        partName: 'Insulation Tape',
        pricePerUnit: 150,
        quantity: 4,
        totalPrice: 600,
      },
      {
        partName: 'Drain Pipe',
        pricePerUnit: 250,
        quantity: 2,
        totalPrice: 500,
      },
      {
        partName: 'Remote Control',
        pricePerUnit: 850,
        quantity: 1,
        totalPrice: 850,
      },
      {
        partName: 'Filter Cleaning',
        pricePerUnit: 300,
        quantity: 1,
        totalPrice: 300,
      },
      {
        partName: 'Labor Charges',
        pricePerUnit: 2000,
        quantity: 1,
        totalPrice: 2000,
      },
    ],
    subtotal: 24800,
    discount: 2800,
    totalAmount: 22000,
    notes: 'Thank you for your business!',
  };

  const itemsHTML = billData.items
    .map(
      (item) => `
    <tr>
      <td style="padding: 8px; border-bottom: 1px solid #ddd;">${
        item.partName
      }</td>
      <td style="padding: 8px; border-bottom: 1px solid #ddd; text-align: center;">₹ ${item.pricePerUnit.toFixed(
        2
      )}</td>
      <td style="padding: 8px; border-bottom: 1px solid #ddd; text-align: center;">${
        item.quantity
      }</td>
      <td style="padding: 8px; border-bottom: 1px solid #ddd; text-align: right;">₹ ${item.totalPrice.toFixed(
        2
      )}</td>
    </tr>
  `
    )
    .join('');

  return `
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body { font-family: Arial, sans-serif; padding: 0 40px 0 40px; }
    h1 { color: #276668; font-size: 48px; margin: 20px 0 30px 0; }
    .invoice-details { display: flex; justify-content: space-between; margin-bottom: 30px; }
    .invoice-parties { display: flex; justify-content: space-between; margin-bottom: 40px; }
    .invoice-from, .invoice-to { width: 45%; }
    .party-title { color: #276668; font-weight: bold; font-size: 16px; margin-bottom: 10px; border-left: 4px solid #276668; padding-left: 10px; }
    .items-table { width: 100%; border-collapse: collapse; margin-bottom: 30px; }
    .table-header { color: #276668; }
    .table-header th { padding: 12px 8px; text-align: left; font-weight: bold; }
    .totals-section { display: flex; justify-content: space-between;page-break-inside: avoid; }
    .totals { width: 45%; text-align: right; }
    .total-row { display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 16px; }
  </style>
</head>
<body>
  <h1>Invoice</h1>
  <div class="invoice-details">
    <div><strong>Date:</strong> ${formatDate(billData.createdAt)}</div>
    <div><strong>Invoice No.:</strong> ${billData.billNumber}</div>
  </div>
  <div class="invoice-parties">
    <div class="invoice-from">
      <div class="party-title">INVOICE FROM:</div>
      <div>${billData.technicianId.companyName}<br>${
    billData.technicianId.address
  }<br>${billData.technicianId.phoneNumber}</div>
    </div>
    <div class="invoice-to">
      <div class="party-title">INVOICE TO:</div>
      <div>${billData.customerId.fullName}<br>${
    billData.customerId.fullAddress
  }<br>${billData.customerId.area}<br>${billData.customerId.contactNumber}</div>
    </div>
  </div>
  <table class="items-table">
    <thead class="table-header">
      <tr><th>Item Description</th><th>Price</th><th>Quantity</th><th>Total</th></tr>
    </thead>
    <tbody>${itemsHTML}</tbody>
  </table>
  <div class="totals-section">
    <div><strong>NOTE:</strong><br>${billData.notes}</div>
    <div class="totals">
      <div class="total-row"><span>SUB TOTAL</span><span>₹ ${billData.subtotal.toFixed(
        2
      )}</span></div>
      <div class="total-row"><span>Discount</span><span>- ₹ ${billData.discount.toFixed(
        2
      )}</span></div>
      <div class="total-row" style="border-top: 2px solid #276668; padding-top: 8px; font-weight: bold;"><span>TOTAL</span><span>₹ ${billData.totalAmount.toFixed(
        2
      )}</span></div>
    </div>
  </div>
</body>
</html>`;
};

export default function App() {
  const [result, setResult] = useState<PdfResult | null>(null);
  const [loading, setLoading] = useState(false);

  const sharePdf = async (filePath: string) => {
    try {
      await Share.share({
        url: 'file://' + filePath,
        title: 'Share PDF',
      });
    } catch (error) {
      Alert.alert('Error', String(error));
    }
  };

  const createFiixxoInvoice = async (
    pageSize: 'A4' | 'A3' | 'A5' | 'Letter' | 'Legal'
  ) => {
    setLoading(true);
    try {
      const res = await generatePdf({
        html: generateInvoiceHTML(),
        fileName: `fiixxo_invoice_${pageSize}.pdf`,
        pageSize: pageSize,
        header: HEADER_HTML,
        footer: FOOTER_HTML,
        headerHeight: 120,
        footerHeight: 90,
        showPageNumbers: true,
        pageNumberFormat: 'Page {page} of {total}',
        pageNumberFontSize: 14,
      });
      setResult(res);
      if (res.success) {
        Alert.alert(
          'Success',
          `Invoice created (${pageSize})!\n\nPath: ${res.filePath}`,
          [
            { text: 'OK' },
            { text: 'Share', onPress: () => sharePdf(res.filePath) },
          ]
        );
      } else {
        Alert.alert('Error', res.error || 'PDF generation failed');
      }
    } catch (error) {
      Alert.alert('Error', String(error));
    }
    setLoading(false);
  };

  const createSimplePdf = async () => {
    setLoading(true);
    try {
      const res = await generatePdf({
        html: '<h1>Hello World</h1><p>This is a simple PDF document.</p>',
        fileName: 'simple.pdf',
        pageSize: 'A4',
      });
      setResult(res);
      if (res.success) {
        Alert.alert('Success', `PDF created!\n\nPath: ${res.filePath}`, [
          { text: 'OK' },
          { text: 'Share', onPress: () => sharePdf(res.filePath) },
        ]);
      } else {
        Alert.alert('Error', res.error || 'PDF generation failed');
      }
    } catch (error) {
      Alert.alert('Error', String(error));
    }
    setLoading(false);
  };

  const createPdfWithHeaderFooter = async () => {
    setLoading(true);
    try {
      const res = await generatePdf({
        html: `
          <h1>Invoice</h1>
          <p>Invoice #12345</p>
          <table style="width:100%; border-collapse: collapse;">
            <tr><th style="border:1px solid black;">Item</th><th style="border:1px solid black;">Price</th></tr>
            <tr><td style="border:1px solid black;">Product 1</td><td style="border:1px solid black;">$100</td></tr>
            <tr><td style="border:1px solid black;">Product 2</td><td style="border:1px solid black;">$200</td></tr>
          </table>
        `,
        fileName: 'invoice.pdf',
        pageSize: 'A4',
        header: '<h3>Company Name</h3>',
        footer: '<p>Thank you for your business!</p>',
        showPageNumbers: true,
        pageNumberFormat: 'Page {page} of {total}',
        marginTop: 60,
        marginBottom: 60,
      });
      setResult(res);
      if (res.success) {
        Alert.alert('Success', `PDF created!\n\nPath: ${res.filePath}`, [
          { text: 'OK' },
          { text: 'Share', onPress: () => sharePdf(res.filePath) },
        ]);
      } else {
        Alert.alert('Error', res.error || 'PDF generation failed');
      }
    } catch (error) {
      Alert.alert('Error', String(error));
    }
    setLoading(false);
  };

  const createMultiPagePdf = async () => {
    setLoading(true);
    try {
      let content = '<h1>Multi-Page Document</h1>';
      for (let i = 1; i <= 5; i++) {
        content += `<h2>Section ${i}</h2><p>${'Lorem ipsum dolor sit amet. '.repeat(
          50
        )}</p>`;
      }

      const res = await generatePdf({
        html: content,
        fileName: 'multipage.pdf',
        pageSize: 'Letter',
        header: '<div style="font-size:12px;">Document Header</div>',
        footer: '<div style="font-size:10px;">Confidential</div>',
        showPageNumbers: true,
        marginTop: 50,
        marginBottom: 50,
        marginLeft: 30,
        marginRight: 30,
      });
      setResult(res);
      if (res.success) {
        Alert.alert('Success', `PDF created!\n\nPath: ${res.filePath}`, [
          { text: 'OK' },
          { text: 'Share', onPress: () => sharePdf(res.filePath) },
        ]);
      } else {
        Alert.alert('Error', res.error || 'PDF generation failed');
      }
    } catch (error) {
      Alert.alert('Error', String(error));
    }
    setLoading(false);
  };

  return (
    <ScrollView style={styles.container}>
      <View style={styles.content}>
        <Text style={styles.title}>React Native Nitro HTML to PDF</Text>

        <Button
          title="Fiixxo Invoice - A4"
          onPress={() => createFiixxoInvoice('A4')}
          disabled={loading}
        />
        <View style={styles.spacer} />
        <Button
          title="Fiixxo Invoice - A3"
          onPress={() => createFiixxoInvoice('A3')}
          disabled={loading}
        />
        <View style={styles.spacer} />
        <Button
          title="Fiixxo Invoice - A5"
          onPress={() => createFiixxoInvoice('A5')}
          disabled={loading}
        />
        <View style={styles.spacer} />
        <Button
          title="Fiixxo Invoice - Letter"
          onPress={() => createFiixxoInvoice('Letter')}
          disabled={loading}
        />
        <View style={styles.spacer} />
        <Button
          title="Fiixxo Invoice - Legal"
          onPress={() => createFiixxoInvoice('Legal')}
          disabled={loading}
        />

        <View style={styles.spacer} />

        <Button
          title="Create Simple PDF"
          onPress={createSimplePdf}
          disabled={loading}
        />

        <View style={styles.spacer} />

        <Button
          title="Create PDF with Header/Footer"
          onPress={createPdfWithHeaderFooter}
          disabled={loading}
        />

        <View style={styles.spacer} />

        <Button
          title="Create Multi-Page PDF"
          onPress={createMultiPagePdf}
          disabled={loading}
        />

        {loading && <Text style={styles.loading}>Generating PDF...</Text>}

        {result && (
          <View style={styles.result}>
            <Text style={styles.resultTitle}>Last Result:</Text>
            <Text>Success: {result.success ? 'Yes' : 'No'}</Text>
            <Text style={styles.path}>Path: {result.filePath}</Text>
            {result.error && (
              <Text style={styles.error}>Error: {result.error}</Text>
            )}
            {result.success && (
              <Button
                title="Share PDF"
                onPress={() => sharePdf(result.filePath)}
              />
            )}
          </View>
        )}

        <View style={styles.footer}>
          <Text style={styles.footerBy}>By</Text>
          <TouchableOpacity
            onPress={() => Linking.openURL('https://github.com/dhruvpatel374')}
          >
            <Text style={styles.footerText}>Dhruv Patel</Text>
          </TouchableOpacity>
          <Text style={styles.footerAt}>at</Text>
          <TouchableOpacity
            onPress={() => Linking.openURL('https://flitzinteractive.com')}
          >
            <Image
              source={require('./images/Flitz_Interactive.png')}
              style={styles.logo}
              resizeMode="contain"
            />
          </TouchableOpacity>
        </View>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  content: {
    padding: 20,
    paddingTop: 60,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 10,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 14,
    color: '#007AFF',
    textAlign: 'center',
    marginBottom: 5,
    textDecorationLine: 'underline',
  },
  company: {
    fontSize: 16,
    fontWeight: '600',
    color: '#276668',
    textAlign: 'center',
    marginBottom: 30,
    textDecorationLine: 'underline',
  },
  spacer: {
    height: 15,
  },
  footer: {
    marginTop: 40,
    paddingTop: 20,
    borderTopWidth: 1,
    borderTopColor: '#ddd',
    alignItems: 'center',
  },
  footerBy: {
    fontSize: 12,
    color: '#999',
    marginBottom: 5,
  },
  footerText: {
    fontSize: 14,
    color: '#007AFF',
    textDecorationLine: 'underline',
  },
  footerAt: {
    fontSize: 12,
    color: '#999',
    marginVertical: 5,
  },
  logo: {
    width: screenWidth * 0.5,
    height: 80,
  },
  footerCompany: {
    fontSize: 16,
    fontWeight: '600',
    color: '#276668',
    textDecorationLine: 'underline',
  },
  loading: {
    marginTop: 20,
    textAlign: 'center',
    fontSize: 16,
    color: '#666',
  },
  result: {
    marginTop: 30,
    padding: 15,
    backgroundColor: '#fff',
    borderRadius: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  resultTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 10,
  },
  path: {
    fontSize: 12,
    color: '#666',
    marginVertical: 5,
  },
  error: {
    color: 'red',
    marginTop: 5,
  },
});
