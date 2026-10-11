# Till Counter

Clean native Android till-counting app. Offline; no permissions, network, WebView, HTML or JavaScript.

Workflow: Store Charges → Gift Certificates → Vendor Coupons → Checks → Loans → Cash denominations → Till Summary.

Counting cash: tap C to clear an unaccepted entry; long-press C to reset the saved count for the selected denomination (or the selected loose/roll mode). The final form displays a cash drop recommendation using available counted denominations, followed by Cash, Checks, Vendor Coupon, Gift Certificates, Store Charges, and Loans. The physical drop may be less than the target if exact change is unavailable; that difference is shown explicitly.

Release proof: native Android 7 form-value checks read paired labels and values after resetting the scroll position; Android 16 instrumentation also resets summary scrolling before verification. Device-test time budgets are bounded separately from the overall 45-minute job limit.
