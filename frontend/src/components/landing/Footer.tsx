import { Link } from "@/i18n/navigation";
import { useTranslations } from "next-intl";

export default function Footer() {
  const t = useTranslations("Landing")
  return (
    <footer className="bg-slate-900 text-slate-300">
      <div className="max-w-6xl mx-auto px-4 py-16">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-8">
          {/* Brand */}
          <div className="col-span-2 md:col-span-1">
            <div className="flex items-center gap-2 mb-3">
              <img src="/logo.png" alt="CacaNode Logo" className="w-7 h-7" />
              <span className="font-bold text-white text-lg">CacaNode</span>
            </div>
            <p className="text-sm text-slate-400 leading-relaxed max-w-xs">
              {t("footer.description")}
            </p>
          </div>

          {/* Product */}
          <div>
            <h4 className="text-white font-semibold text-sm mb-4">{t("footer.product")}</h4>
            <ul className="space-y-2.5 text-sm">
              <li><a href="#features" className="hover:text-white transition-colors">{t("footer.features")}</a></li>
              <li><Link href="/pricing" className="hover:text-white transition-colors">{t("footer.pricing")}</Link></li>
              <li><Link href="/documentation" className="hover:text-white transition-colors">{t("footer.documentation")}</Link></li>
              <li><a href="#how-it-works" className="hover:text-white transition-colors">{t("footer.howItWorks")}</a></li>
              <li><Link href="/widget/preview" className="hover:text-white transition-colors">{t("footer.widgetDemo")}</Link></li>
            </ul>
          </div>

          {/* Company */}
          <div>
            <h4 className="text-white font-semibold text-sm mb-4">{t("footer.company")}</h4>
            <ul className="space-y-2.5 text-sm">
              <li><a href="#" className="hover:text-white transition-colors">{t("footer.about")}</a></li>
              <li><a href="#" className="hover:text-white transition-colors">{t("footer.blog")}</a></li>
              <li><a href="#" className="hover:text-white transition-colors">{t("footer.careers")}</a></li>
              <li><a href="#" className="hover:text-white transition-colors">{t("footer.contact")}</a></li>
            </ul>
          </div>

          {/* Legal */}
          <div>
            <h4 className="text-white font-semibold text-sm mb-4">{t("footer.legal")}</h4>
            <ul className="space-y-2.5 text-sm">
              <li><a href="#" className="hover:text-white transition-colors">{t("footer.privacy")}</a></li>
              <li><a href="#" className="hover:text-white transition-colors">{t("footer.terms")}</a></li>
              <li><a href="#" className="hover:text-white transition-colors">{t("footer.cookies")}</a></li>
              <li><a href="#" className="hover:text-white transition-colors">{t("footer.security")}</a></li>
            </ul>
          </div>
        </div>
      </div>

      <div className="border-t border-slate-800">
        <div className="max-w-6xl mx-auto px-4 py-6 flex flex-col sm:flex-row items-center justify-between gap-4 text-sm text-slate-500">
          <p>{t("footer.copyright")}</p>
          <p>{t("footer.madeFor")}</p>
        </div>
      </div>
    </footer>
  );
}
