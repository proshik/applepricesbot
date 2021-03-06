package ru.proshik.applepricebot.service.provider;

import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSpan;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.proshik.applepricebot.exception.ProviderParseException;
import ru.proshik.applepricebot.repository.model.Product;
import ru.proshik.applepricebot.repository.model.ProductType;
import ru.proshik.applepricebot.repository.model.Provider;
import ru.proshik.applepricebot.utils.ProviderUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static ru.proshik.applepricebot.utils.ProviderUtils.extractParameters;

@Component
@Qualifier("ajScreeningProvider")
public class AjScreeningProvider implements ScreeningProvider {

    private static final Logger LOG = Logger.getLogger(AjScreeningProvider.class);

    private final HtmlPageProvider htmlPageProvider;

    @Autowired
    public AjScreeningProvider(HtmlPageProvider htmlPageProvider) {
        this.htmlPageProvider = htmlPageProvider;
    }

    private static class ProductTypeInfo {
        ProductType productType;
        String xPathToItems;
        String titleRegExp;

        ProductTypeInfo(ProductType productType, String xPathToItems, String titleRegExp) {
            this.productType = productType;
            this.xPathToItems = xPathToItems;
            this.titleRegExp = titleRegExp;
        }
    }

    private static Map<ProductType, ProductTypeInfo> ALL_PRODUCT_TYPE_INFOS = new HashMap<>();

    static {

        ALL_PRODUCT_TYPE_INFOS.put(ProductType.IPHONE_SE,
                new ProductTypeInfo(ProductType.IPHONE_SE, "//li[@id='iphone5se']/article[contains(@class, 'iphone5se')]/ul/li", "iPhone SE.*"));
        ALL_PRODUCT_TYPE_INFOS.put(ProductType.IPHONE_6S,
                new ProductTypeInfo(ProductType.IPHONE_6S, "//li[@id='iphone'][7]/article[contains(@class, 'iphone6s')]/ul/li", "iPhone 6s.*"));
        ALL_PRODUCT_TYPE_INFOS.put(ProductType.IPHONE_7,
                new ProductTypeInfo(ProductType.IPHONE_7, "//li[@id='iphone'][6]/article[contains(@class, 'iphone7')]/ul/li", "iPhone 7(?!.*Plus).*"));
        ALL_PRODUCT_TYPE_INFOS.put(ProductType.IPHONE_7_PLUS,
                new ProductTypeInfo(ProductType.IPHONE_7_PLUS, "//li[@id='iphone'][6]/article[contains(@class, 'iphone7')]/ul/li", "iPhone 7 Plus.*"));
        ALL_PRODUCT_TYPE_INFOS.put(ProductType.IPHONE_8,
                new ProductTypeInfo(ProductType.IPHONE_8, "//li[@id='iphone'][5]/article[contains(@class, 'iphone8')]/ul/li", "iPhone 8(?!.*Plus).*"));
        ALL_PRODUCT_TYPE_INFOS.put(ProductType.IPHONE_8_PLUS,
                new ProductTypeInfo(ProductType.IPHONE_8_PLUS, "//li[@id='iphone'][5]/article[contains(@class, 'iphone8')]/ul/li", "iPhone 8 Plus.*"));
        ALL_PRODUCT_TYPE_INFOS.put(ProductType.IPHONE_X,
                new ProductTypeInfo(ProductType.IPHONE_X, "//li[@id='iphone'][3]/article[contains(@class, 'iphoneX')]/ul/li", "iPhone X.*"));
        ALL_PRODUCT_TYPE_INFOS.put(ProductType.IPHONE_XS,
                new ProductTypeInfo(ProductType.IPHONE_XS, "//li[@id='iphone']/article[contains(@class, 'iphoneXS')]/ul/li", "iPhone XS(?!.*Max).*"));
        ALL_PRODUCT_TYPE_INFOS.put(ProductType.IPHONE_XS_MAX,
                new ProductTypeInfo(ProductType.IPHONE_XS_MAX, "//li[@id='iphone']/article[contains(@class, 'iphoneXS')]/ul/li", "iPhone XS Max.*"));
        ALL_PRODUCT_TYPE_INFOS.put(ProductType.IPHONE_XR,
                new ProductTypeInfo(ProductType.IPHONE_XR, "//li[@id='iphone'][2]/article[contains(@class, 'iphoneXR')]/ul/li", "iPhone XR.*"));
    }

    @Override
    public List<Product> screening(Provider provider) throws ProviderParseException {
        return screening(provider, Arrays.asList(ProductType.values()));
    }

    @Override
    public List<Product> screening(Provider provider, ProductType productType) throws ProviderParseException {
        return screening(provider, Collections.singletonList(productType));
    }

    @Override
    public List<Product> screening(Provider provider, List<ProductType> productTypes) throws ProviderParseException {
        LOG.info("ScreeningProvider has started for " + provider.getTitle());

        List<ProductTypeInfo> typeInfos = productTypes.stream()
                .map(productType -> ALL_PRODUCT_TYPE_INFOS.get(productType))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        HtmlPage page = htmlPageProvider.provide(provider.getUrl());

        List<Product> products = new ArrayList<>();
        for (ProductTypeInfo pti : typeInfos) {
            products.addAll(buildProduct(page, pti));
        }

        LOG.info("ScreeningProvider has ended for " + provider.getTitle());

        return products;
    }

    private List<Product> buildProduct(HtmlPage page, ProductTypeInfo pti) {
        List<Product> products = new ArrayList<>();

        Pattern p = Pattern.compile(pti.titleRegExp);

        List<HtmlElement> elements = page.getByXPath(pti.xPathToItems);
        String description = elements.get(0).asText();
        for (HtmlElement element : elements.stream().skip(1).collect(Collectors.toList())) {
            // if line is null or empty then skip that element
            if (StringUtils.isEmpty(element.asText().trim())) {
                continue;
            }

            String title = element.getFirstChild()
                    .getNodeValue().substring(0, element.getFirstChild().getNodeValue().length() - 3);
            // if title not match to regExp the skip elemtn
            if (!p.matcher(title).matches()) {
                // if this element not equals title from holder
                continue;
            }

            // try to find a price
            List<HtmlSpan> spans = element.getByXPath("./span");
            BigDecimal price = null;
            for (HtmlSpan span : spans) {
                try {
                    price = new BigDecimal(span.asText().replace(" ", ""));
                } catch (Exception e) {
                    LOG.debug("price not found for type=" + pti.productType + " and xPath=" + pti.xPathToItems);
                }
            }

            // build price object
            products.add(Product.builder()
                    .title(title)
                    .description(description)
                    .price(price)
                    .productType(pti.productType)
                    .parameters(ProviderUtils.paramsToString(extractParameters(element.asText())))
                    .build());
        }

        return products;
    }

    // XS
    // for title: iPhone XS(?!.*Max).*.GB
    // for description: (?<=GB ).*
    // for parameters: (?<=XS ).*GB

    // XS Max
    // for title: iPhone XS Max.*.GB
    // for description: (?<=GB ).*
    // for parameters: (?<=Max ).*GB
}