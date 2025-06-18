#!/usr/bin/env bb

(require '[clojure.data.csv :as csv]
         '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[cheshire.core :as json]
         '[babashka.http-client :as http])

(defn base64-url-encode [data]
  "Base64 URL encode (for JWT)"
  (-> (java.util.Base64/getUrlEncoder)
      (.withoutPadding)
      (.encodeToString data)))

(defn get-google-access-token [client-email private-key]
  (let [now (quot (System/currentTimeMillis) 1000)

        ;; JWT Header
        header {:alg "RS256" :typ "JWT"}
        header-json (json/generate-string header)
        header-b64 (base64-url-encode (.getBytes header-json "UTF-8"))

        ;; JWT Payload
        payload {:iss client-email
                 :scope "https://www.googleapis.com/auth/spreadsheets.readonly"
                 :aud "https://oauth2.googleapis.com/token"
                 :exp (+ now 3600)
                 :iat now}
        payload-json (json/generate-string payload)
        payload-b64 (base64-url-encode (.getBytes payload-json "UTF-8"))

        ;; JWT unsigned token
        unsigned-token (str header-b64 "." payload-b64)

        ;; Parse private key
        private-key-clean (-> private-key
                              (str/replace "-----BEGIN PRIVATE KEY-----" "")
                              (str/replace "-----END PRIVATE KEY-----" "")
                              (str/replace #"\\n" "\n")
                              (str/replace #"\n" "")
                              (str/replace #"\s" "")
                              str/trim)

        key-bytes (.decode (java.util.Base64/getDecoder) private-key-clean)
        key-spec (java.security.spec.PKCS8EncodedKeySpec. key-bytes)
        private-key-obj (.generatePrivate (java.security.KeyFactory/getInstance "RSA") key-spec)

        ;; Sign the token
        signature-obj (java.security.Signature/getInstance "SHA256withRSA")
        _ (.initSign signature-obj private-key-obj)
        _ (.update signature-obj (.getBytes unsigned-token "UTF-8"))
        signature-bytes (.sign signature-obj)
        signature-b64 (base64-url-encode signature-bytes)

        ;; Complete JWT
        jwt (str unsigned-token "." signature-b64)

        ;; Request access token
        response (http/post "https://oauth2.googleapis.com/token"
                           {:form-params {:grant_type "urn:ietf:params:oauth:grant-type:jwt-bearer"
                                          :assertion jwt}
                            :content-type :x-www-form-urlencoded})]

    (if (= 200 (:status response))
      (-> response :body (json/parse-string true) :access_token)
      (do
        (println "Error in token response:" (:status response))
        (println "Response body:" (:body response))
        (throw (Exception. "Failed to get access token"))))))

(defn read-google-sheet [spreadsheet-id sheet-name creds-file]
  (let [creds (json/parse-string (slurp creds-file) true)
        token (get-google-access-token (:client_email creds) (:private_key creds))
        range-param (str sheet-name "!A:Z")
        url (str "https://sheets.googleapis.com/v4/spreadsheets/"
                 spreadsheet-id
                 "/values/"
                 (java.net.URLEncoder/encode range-param "UTF-8"))
        response (http/get url {:headers {"Authorization" (str "Bearer " token)}})]

    (when (= 200 (:status response))
      (-> response :body (json/parse-string true) :values))))

;; Helper functions
(defn normalize-string [s]
  "Normalize string for URL/ID generation"
  (-> (or s "")
      str/lower-case
      (str/replace #"[άα]" "a")
      (str/replace #"[έε]" "e")
      (str/replace #"[ήη]" "h")
      (str/replace #"[ίι]" "i")
      (str/replace #"[όο]" "o")
      (str/replace #"[ύυ]" "u")
      (str/replace #"[ώω]" "w")
      (str/replace #"[^a-z0-9\s-]" "")
      (str/replace #"\s+" "-")
      str/trim))

(defn normalize-string-gr [s]
  "Normalize string for URL/ID generation"
  (-> (or s "")
      str/lower-case
      (str/replace #"[ά]" "α")
      (str/replace #"[έ]" "ε")
      (str/replace #"[ή]" "η")
      (str/replace #"[ί]" "ι")
      (str/replace #"[ό]" "ο")
      (str/replace #"[ύ]" "υ")
      (str/replace #"[ώ]" "ω")
      str/trim))

(defn generate-permalink [name]
  "Generate permalink from union name"
  (str "/somateio-" (normalize-string name) "/"))

(defn generate-id [name]
  "Generate unique ID from union name"
  (let [normalized (normalize-string name)]
    (if (str/blank? normalized)
      (str "union-" (hash name))
      normalized)))

(defn parse-phone [phone-str]
  "Parse phone number"
  (when-not (str/blank? phone-str)
    (let [trimmed (str/trim phone-str)
          prefix (if (str/starts-with? trimmed "30") "+" "+30")]
      (str prefix trimmed))))

(defn parse-phones [phone-str]
  "Parse comma-separated phone numbers"
  (when-not (str/blank? phone-str)
    (map parse-phone (str/split phone-str #","))))

(defn parse-emails [s]
  "Parse comma-separated emails"
  (when-not (str/blank? s)
    (->> (str/split s #",")
         (map str/trim)
         (filter #(not (str/blank? %)))
         (vec))))

(defn clean-url [url]
  "Clean and validate URL"
  (when-not (str/blank? url)
    (let [cleaned (str/trim url)]
      (when (or (str/starts-with? cleaned "http")
                (str/starts-with? cleaned "www."))
        cleaned))))

(defn process-union-entry [row]
  "Process raw CSV data into structured union data"
  (let [short-name        (get row "Όνομα σωματείου")
        full-name         (get row "Πλήρες όνομα")
        union-type        (get row "Τύπος σωματείου")
        parent-union      (get row "Κλαδικό σωματείο")
        website           (clean-url (get row "Ιστοσελίδα σωματείου"))
        description       (get row "Περιγραφή")
        registration-form (clean-url (get row "Φόρμα εγγραφής"))
        phones            (parse-phones (get row "Τηλέφωνα"))
        emails            (parse-emails (get row "Εmail"))
        instagram         (clean-url (get row "Instagram"))
        other-contact     (get row "Άλλοι τρόποι επικοινωνίας")
        sector            (let [low (-> full-name normalize-string-gr str/lower-case)]
                            (cond
                              (re-find #"(νοσοκομ|ιατρ|υγε)" low) "healthcare"
                              (re-find #"(εκπαιδ|διδασκ|φροντιστ)" low) "education"
                              (re-find #"(λογιστ)" low) "accounting"
                              (re-find #"(ερευν|επιστημ)" low) "research"
                              (re-find #"(τουριστ|επισιτ|εστιατ)" low) "tourism"
                              (re-find #"(οικοδομ|κατασκευ|τεχνικ)" low) "construction"
                              (re-find #"(τηλεπικοινων|πληροφορικ|τεχνολογ)" low) "tech"
                              (re-find #"(εμπορ|πωλ)" low) "commerce"
                              (re-find #"(μεταλλ|χημικ|βιομηχαν)" low) "industry"
                              (re-find #"(τροφιμ|γαλακτ|ποτ)" low) "food"
                              (re-find #"(συγκοινων|οδηγ|μεταφορ)" low) "transport"
                              (re-find #"(διοικητικ|γραμματε)" low) "administration"
                              (re-find #"(δημοσι|δημοτικ)" low) "public"
                              (re-find #"(συνδικ|εργατ|εργαζ)" low) "labor"
                              (re-find #"(γυναικ)" low) "social"
                              :else "other"))
        sector-gr        (get {"healthcare" "Υγεία"
                               "education" "Εκπαίδευση"
                               "accounting" "Λογιστικά"
                               "research" "Έρευνα"
                               "tourism" "Τουρισμός"
                               "construction" "Κατασκευές"
                               "tech" "ΤΠΕ (Τηλεπικοινωνίες - Πληροφορική - Έρευνα)"
                               "commerce" "Εμπόριο"
                               "industry" "Βιομηχανία"
                               "food" "Τρόφιμα"
                               "transport" "Μεταφορές"
                               "administration" "Διοίκηση"
                               "public" "Δημόσιος Τομέας"
                               "labor" "Εργασιακά"
                               "social" "Κοινωνικά"
                               "other" "Άλλοι"} sector)]
    {:id (generate-id short-name)
     :short-name short-name
     :full-name full-name
     :name (if (str/blank? full-name) short-name full-name)
     :type union-type
     :parent-union (when-not (str/blank? parent-union) parent-union)
     :website website
     :description (when-not (str/blank? description) description)
     :registration-form registration-form
     :phones phones
     :emails emails
     :instagram instagram
     :other-contact (when-not (str/blank? other-contact) other-contact)
     :permalink (generate-permalink short-name)
     :sector sector
     :sector-name sector-gr}))

(defn clj-to-json [data]
  "Convert Clojure data to JSON string"
  (cond
    (nil? data) "null"
    (string? data) (str "\"" (str/escape data {\" "\\\"", \\ "\\\\"}) "\"")
    (number? data) (str data)
    (boolean? data) (if data "true" "false")
    (keyword? data) (str "\"" (name data) "\"")
    (sequential? data) (str "[" (str/join "," (map clj-to-json data)) "]")
    (map? data) (str "{"
                     (str/join ","
                               (map (fn [[k v]]
                                      (str (clj-to-json (name k)) ":" (clj-to-json v)))
                                    data))
                     "}")
    :else (str "\"" (str data) "\"")))

(defn generate-js-data [unions]
  "Generate JavaScript data array"
  (let [js-unions (mapv (fn [union]
                          {"id" (:id union)
                           "name" (:name union)
                           "shortName" (:short-name union)
                           "type" (:type union)
                           "sector" (:sector union)
                           "sectorName" (:sector-name union)
                           "parentUnion" (:parent-union union)
                           "website" (:website union)
                           "description" (:description union)
                           "registrationForm" (:registration-form union)
                           "phones" (vec (:phones union))
                           "emails" (vec (:emails union))
                           "instagram" (:instagram union)
                           "otherContact" (:other-contact union)
                           "permalink" (:permalink union)})
                        unions)]
    (str "const unionData = " (clj-to-json js-unions) ";")))

(def html-template
  "<!DOCTYPE html>
<html lang=\"el\">
<head>
    <meta charset=\"UTF-8\">
    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
    <title>Βρες το Σωματείο σου</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            color: #333;
        }

        .container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
        }

        .header {
            text-align: center;
            color: white;
            margin-bottom: 40px;
        }

        .header h1 {
            font-size: 2.5rem;
            margin-bottom: 10px;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
        }

        .header p {
            font-size: 1.2rem;
            opacity: 0.9;
        }

        .search-section {
            background: white;
            border-radius: 20px;
            padding: 40px;
            box-shadow: 0 20px 40px rgba(0,0,0,0.1);
            margin-bottom: 30px;
        }

        .search-container {
            position: relative;
            margin-bottom: 25px;
        }

        .search-input {
            width: 100%;
            padding: 15px 50px 15px 20px;
            border: 2px solid #e0e0e0;
            border-radius: 15px;
            font-size: 1.1rem;
            transition: all 0.3s ease;
        }

        .search-input:focus {
            outline: none;
            border-color: #667eea;
            transform: translateY(-2px);
            box-shadow: 0 5px 15px rgba(102, 126, 234, 0.3);
        }

        .search-icon {
            position: absolute;
            right: 20px;
            top: 50%;
            transform: translateY(-50%);
            color: #999;
            font-size: 1.2rem;
        }

        .search-suggestions {
            position: absolute;
            top: 100%;
            left: 0;
            right: 0;
            background: white;
            border: 1px solid #e0e0e0;
            border-radius: 10px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.1);
            z-index: 1000;
            max-height: 300px;
            overflow-y: auto;
            display: none;
        }

        .suggestion-item {
            padding: 12px 20px;
            cursor: pointer;
            border-bottom: 1px solid #f0f0f0;
            transition: background 0.2s;
        }

        .suggestion-item:hover {
            background: #f8f9ff;
        }

        .suggestion-item:last-child {
            border-bottom: none;
        }

        .suggestion-name {
            font-weight: 600;
            color: #333;
        }

        .suggestion-type {
            font-size: 0.9rem;
            color: #666;
            margin-top: 2px;
        }

        .form-group {
            margin-bottom: 25px;
        }

        label {
            display: block;
            margin-bottom: 8px;
            font-weight: 600;
            color: #555;
            font-size: 1.1rem;
        }

        select {
            width: 100%;
            padding: 15px;
            border: 2px solid #e0e0e0;
            border-radius: 10px;
            font-size: 1rem;
            background: white;
            transition: all 0.3s ease;
        }

        select:focus {
            outline: none;
            border-color: #667eea;
            transform: translateY(-2px);
            box-shadow: 0 5px 15px rgba(102, 126, 234, 0.3);
        }

        .clear-btn {
            background: #f0f0f0;
            color: #666;
            border: none;
            padding: 10px 20px;
            border-radius: 25px;
            font-size: 0.9rem;
            cursor: pointer;
            transition: all 0.3s ease;
            margin-top: 15px;
        }

        .clear-btn:hover {
            background: #e0e0e0;
        }

        .results-section {
            background: white;
            border-radius: 20px;
            padding: 40px;
            box-shadow: 0 20px 40px rgba(0,0,0,0.1);
            display: none;
        }

        .union-card {
            background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
            color: white;
            padding: 30px;
            border-radius: 15px;
            margin-bottom: 20px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.2);
            position: relative;
        }

        .permalink-badge {
            position: absolute;
            top: 15px;
            right: 15px;
            background: rgba(255,255,255,0.2);
            padding: 5px 12px;
            border-radius: 15px;
            font-size: 0.8rem;
            cursor: pointer;
            transition: all 0.3s ease;
        }

        .permalink-badge:hover {
            background: rgba(255,255,255,0.3);
        }

        .union-card h3 {
            font-size: 1.8rem;
            margin-bottom: 15px;
            padding-right: 100px;
            cursor: pointer;
            transition: opacity 0.3s ease;
        }

        .union-card h3:hover {
            opacity: 0.8;
        }

        .type-badge {
            background: rgba(255,255,255,0.2);
            padding: 5px 15px;
            border-radius: 20px;
            font-size: 0.9rem;
            display: inline-block;
            margin-bottom: 20px;
        }

        .parent-union {
            background: rgba(255,255,255,0.1);
            padding: 10px 15px;
            border-radius: 8px;
            margin-bottom: 20px;
            font-size: 0.95rem;
        }

        .description {
            background: rgba(255,255,255,0.1);
            padding: 15px;
            border-radius: 8px;
            margin-bottom: 20px;
            font-style: italic;
        }

        .contact-info {
            background: rgba(255,255,255,0.1);
            padding: 20px;
            border-radius: 10px;
            margin: 20px 0;
        }

        .contact-info h4 {
            margin-bottom: 15px;
            font-size: 1.3rem;
        }

        .contact-item {
            margin-bottom: 10px;
            display: flex;
            align-items: flex-start;
            flex-wrap: wrap;
        }

        .contact-item strong {
            min-width: 100px;
            margin-right: 10px;
        }

        .contact-link {
            color: white;
            text-decoration: underline;
        }

        .contact-link:hover {
            opacity: 0.8;
        }

        .phone-list, .email-list {
            display: flex;
            flex-direction: column;
            gap: 5px;
        }

        .register-link, .social-link {
            background: rgba(255,255,255,0.2);
            color: white;
            text-decoration: none;
            padding: 12px 25px;
            border-radius: 25px;
            display: inline-block;
            margin: 10px 10px 0 0;
            transition: all 0.3s ease;
            font-weight: 600;
        }

        .register-link:hover, .social-link:hover {
            background: rgba(255,255,255,0.3);
            transform: translateY(-2px);
        }

        .header a:hover h1 {
            opacity: 0.8;
            transform: scale(1.02);
        }

        .no-results {
            text-align: center;
            color: #666;
            font-size: 1.1rem;
            padding: 40px;
        }

        .url-display {
            background: #f8f9ff;
            border: 1px solid #e0e0e0;
            border-radius: 8px;
            padding: 10px 15px;
            margin-top: 20px;
            font-family: 'Courier New', monospace;
            font-size: 0.9rem;
            color: #666;
        }

        @media (max-width: 768px) {
            .header h1 {
                font-size: 2rem;
            }

            .search-section, .results-section {
                padding: 25px;
            }

            .union-card h3 {
                padding-right: 0;
                margin-bottom: 25px;
            }

            .permalink-badge {
                position: static;
                display: inline-block;
                margin-bottom: 15px;
            }

            .contact-item {
                flex-direction: column;
            }

            .contact-item strong {
                margin-bottom: 5px;
            }
        }
    </style>
</head>
<body>
    <div class=\"container\">
        <div class=\"header\">
            <a href=\"#\" onclick=\"resetToHome(); return false;\" style=\"text-decoration: none; color: inherit;\">
                <h1>🏛️ Κατάλογος Εργατικών Σωματείων</h1>
            </a>
            <p>Βρες το σωματείο σου!</p>
        </div>

        <div class=\"search-section\">
            <div class=\"search-container\">
                <input type=\"text\" class=\"search-input\" id=\"searchInput\" placeholder=\"Αναζήτηση σωματείου... (π.χ. 'ιατρών', 'εκπαίδευση', 'λογιστών')\">
                <span class=\"search-icon\">🔍</span>
                <div class=\"search-suggestions\" id=\"searchSuggestions\"></div>
            </div>

            <div class=\"form-group\">
                <label for=\"sector\">Φιλτράρισμα ανά κλάδο:</label>
                <select id=\"sector\">
                    <option value=\"\">-- Όλοι οι Κλάδοι --</option>
                </select>
            </div>

            <div class=\"form-group\">
                <label for=\"type\">Φιλτράρισμα ανά τύπο:</label>
                <select id=\"type\">
                    <option value=\"\">-- Όλοι οι Τύποι --</option>
                    <option value=\"Κλαδικό\">Κλαδικό Σωματείο</option>
                    <option value=\"Επιχειρησιακό\">Επιχειρησιακό Σωματείο</option>
                </select>
            </div>

            <button class=\"clear-btn\" onclick=\"clearFilters()\">🗑️ Καθαρισμός Φίλτρων</button>
        </div>

        <div class=\"results-section\" id=\"results\">
            <!-- Τα αποτελέσματα θα εμφανιστούν εδώ -->
        </div>

        <div class=\"url-display\" id=\"urlDisplay\" style=\"display: none;\">
            <strong>Permalink:</strong> <span id=\"currentUrl\"></span>
        </div>
    </div>

    <script>
        ~JS_DATA~

        // Helper functions
        function fuzzySearch(query, text) {
            if (!query || !text) return 0;
            query = query.toLowerCase();
            text = text.toLowerCase();

            if (text.includes(query)) return 100;

            let score = 0;
            let queryIndex = 0;

            for (let i = 0; i < text.length && queryIndex < query.length; i++) {
                if (text[i] === query[queryIndex]) {
                    score++;
                    queryIndex++;
                }
            }

            return (score / query.length) * 100;
        }

        function searchUnions(query) {
            if (!query.trim()) return unionData;

            return unionData.map(union => {
                const nameScore = fuzzySearch(query, union.name);
                const shortNameScore = fuzzySearch(query, union.shortName);
                const sectorScore = fuzzySearch(query, union.sectorName);
                const descScore = union.description ? fuzzySearch(query, union.description) : 0;
                const maxScore = Math.max(nameScore, shortNameScore, sectorScore, descScore);

                return { union, score: maxScore };
            }).filter(result => result.score > 20)
              .sort((a, b) => b.score - a.score)
              .map(result => result.union);
        }

        function populateFilters() {
            const sectors = [...new Set(unionData.map(u => u.sectorName))].sort();
            const sectorSelect = document.getElementById('sector');

            sectors.forEach(sector => {
                const option = document.createElement('option');
                option.value = sector;
                option.textContent = sector;
                sectorSelect.appendChild(option);
            });
        }

        function createUnionCard(union) {
            return `
                <div class=\"union-card\">
                    <div class=\"permalink-badge\" onclick=\"copyPermalink('${union.id}')\" title=\"Αντιγραφή permalink\">
                        🔗 Link
                    </div>
                    <h3 onclick=\"focusUnion('${union.id}')\">${union.name}</h3>
                    <div class=\"type-badge\">${union.type} Σωματείο</div>

                    ${union.parentUnion ? `
                        <div class=\"parent-union\">
                            <strong>Μητρικό Σωματείο:</strong> ${union.parentUnion}
                        </div>
                    ` : ''}

                    ${union.description ? `
                        <div class=\"description\">
                            ${union.description}
                        </div>
                    ` : ''}

                    <div class=\"contact-info\">
                        <h4>📞 Στοιχεία Επικοινωνίας</h4>
                        ${union.phones && union.phones.length > 0 ? `
                            <div class=\"contact-item\">
                                <strong>Τηλέφωνα:</strong>
                                <div class=\"phone-list\">
                                    ${union.phones.map(phone => `<a href=\"tel:${phone}\" class=\"contact-link\">${phone}</a>`).join('')}
                                </div>
                            </div>
                        ` : ''}
                        ${union.emails && union.emails.length > 0 ? `
                            <div class=\"contact-item\">
                                <strong>Email:</strong>
                                <div class=\"email-list\">
                                    ${union.emails.map(email => `<a href=\"mailto:${email}\" class=\"contact-link\">${email}</a>`).join('')}
                                </div>
                            </div>
                        ` : ''}
                        ${union.website ? `<div class=\"contact-item\"><strong>Ιστοσελίδα:</strong> <a href=\"${union.website}\" target=\"_blank\" class=\"contact-link\">${union.website}</a></div>` : ''}
                        ${union.otherContact ? `<div class=\"contact-item\"><strong>Άλλοι τρόποι:</strong> <a href=\"${union.otherContact}\" target=\"_blank\" class=\"contact-link\">Δείτε εδώ</a></div>` : ''}
                    </div>

                    <div>
                        ${union.registrationForm ? `
                            <a href=\"${union.registrationForm}\" target=\"_blank\" class=\"register-link\">
                                📝 Φόρμα Εγγραφής
                            </a>
                        ` : ''}
                        ${union.instagram ? `
                            <a href=\"${union.instagram}\" target=\"_blank\" class=\"social-link\">
                                📸 Instagram
                            </a>
                        ` : ''}
                    </div>
                </div>
            `;
        }

        function showResults(unions) {
            const resultsDiv = document.getElementById('results');

            if (unions.length === 0) {
                resultsDiv.innerHTML = '<div class=\"no-results\">Δεν βρέθηκαν αποτελέσματα.</div>';
            } else {
                const html = unions.map(union => createUnionCard(union)).join('');
                resultsDiv.innerHTML = html;
            }

            resultsDiv.style.display = 'block';
        }

        function showSearchSuggestions(unions) {
            const suggestionsDiv = document.getElementById('searchSuggestions');

            if (unions.length === 0) {
                suggestionsDiv.style.display = 'none';
                return;
            }

            const html = unions.slice(0, 5).map(union => `
                <div class=\"suggestion-item\" onclick=\"selectUnion('${union.id}')\">
                    <div class=\"suggestion-name\">${union.name}</div>
                    <div class=\"suggestion-type\">${union.type} Σωματείο - ${union.sectorName}</div>
                </div>
            `).join('');

            suggestionsDiv.innerHTML = html;
            suggestionsDiv.style.display = 'block';
        }

        function selectUnion(unionId) {
            focusUnion(unionId);
            document.getElementById('searchSuggestions').style.display = 'none';
        }

        function performSearch() {
            const query = document.getElementById('searchInput').value;
            const sector = document.getElementById('sector').value;
            const type = document.getElementById('type').value;

            let results = unionData;

            if (query.trim()) {
                results = searchUnions(query);
                showSearchSuggestions(results);
            } else {
                document.getElementById('searchSuggestions').style.display = 'none';
            }

            if (sector) {
                results = results.filter(union => union.sectorName === sector);
            }

            if (type) {
                results = results.filter(union => union.type === type);
            }

            showResults(results);
        }

        function focusUnion(unionId) {
            const union = unionData.find(u => u.id === unionId);
            if (union) {
                showResults([union]);
                window.location.hash = unionId;
                document.getElementById('searchInput').value = union.name;
                document.getElementById('results').scrollIntoView({ behavior: 'smooth' });
            }
        }

        function copyPermalink(unionId) {
            const url = `${window.location.origin}${window.location.pathname}#${unionId}`;
            navigator.clipboard.writeText(url).then(() => {
                // Visual feedback - change icon temporarily
                const badge = event.target;
                const originalText = badge.textContent;
                badge.textContent = '✅ Copied';
                setTimeout(() => {
                    badge.textContent = originalText;
                }, 2000);
            });
        }

        function checkHashOnLoad() {
            const hash = window.location.hash.substring(1);
            if (hash) {
                const union = unionData.find(u => u.id === hash);
                if (union) {
                    focusUnion(hash);
                }
            }
        }

        function clearFilters() {
            document.getElementById('searchInput').value = '';
            document.getElementById('sector').value = '';
            document.getElementById('type').value = '';
            document.getElementById('searchSuggestions').style.display = 'none';

            showResults(unionData);
            window.location.hash = '';
        }

        function resetToHome() {
            clearFilters();
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }

        // Event listeners
        document.getElementById('searchInput').addEventListener('input', performSearch);
        document.getElementById('sector').addEventListener('change', performSearch);
        document.getElementById('type').addEventListener('change', performSearch);

        document.getElementById('searchInput').addEventListener('blur', function() {
            setTimeout(() => {
                document.getElementById('searchSuggestions').style.display = 'none';
            }, 200);
        });

        // Initialize
        populateFilters();
        checkHashOnLoad();
        if (!window.location.hash) {
            showResults(unionData);
        }
    </script>
</body>
</html>")

(defn generate-html [unions]
  "Generate the complete HTML file with union data"
  (let [js-data (generate-js-data unions)]
    (str/replace html-template "~JS_DATA~" js-data)))

(defn read-csv-file [filename]
  "Read and parse CSV file"
  (with-open [reader (io/reader filename)]
    (doall
      (csv/read-csv reader))))

(defn csv-to-maps [csv-data]
  "Convert CSV data to maps using first row as headers"
  (let [headers (first csv-data)
        rows (rest csv-data)]
    (map #(zipmap headers %) rows)))

(defn -main [& args]
  (let [creds-file (or (first args) "credentials.json")
        spreadsheet-id (or (second args) "YOUR_SPREADSHEET_ID")
        sheet-name (or (nth args 2) "Sheet1")
        output-file (or (nth args 3) "index.html")]

    (println "🔐 Authenticating with Google Sheets API...")
    (try
      (let [sheet-data (read-google-sheet spreadsheet-id sheet-name creds-file)
            headers (first sheet-data)
            rows (rest sheet-data)
            raw-unions (map #(zipmap headers %) rows)
            processed-unions (map process-union-entry raw-unions)
            html-content (generate-html processed-unions)]

        (println (str "✅ Processed " (count processed-unions) " unions from Google Sheet"))
        (println "📊 Union breakdown:")
        (doseq [[sector count] (frequencies (map :sector-name processed-unions))]
          (println (str "   " sector ": " count)))

        (println (str "💾 Writing HTML file: " output-file))
        (spit output-file html-content)
        (println "🎉 Site generated successfully!")
        (println (str "📂 Open " output-file " in your browser"))
        (println "🚀 Ready for GitHub Pages!"))

      (catch Exception e
        (println (str "❌ Error: " (.getMessage e)))
        (.printStackTrace e)
        (System/exit 1)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
