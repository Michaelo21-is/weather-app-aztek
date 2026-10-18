import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import LoadUserDetails from "./component/userDetails/LoadUserDetails";
import ToastProvider from "./component/toast/Toast";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata = {
  title: "Weather",
  description: "Current conditions and the 5-day forecast for your location or any city.",
};

export const viewport = {
  themeColor: "#0b1530",
};

export default function RootLayout({ children }) {
  return (
    <html
      lang="en"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full">
        <ToastProvider>
          <LoadUserDetails>{children}</LoadUserDetails>
        </ToastProvider>
      </body>
    </html>
  );
}
