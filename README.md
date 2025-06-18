# 🏛️ Κατάλογος Εργατικών Σωματείων

Μια διαδραστική ιστοσελίδα για την αναζήτηση και ανακάλυψη εργατικών σωματείων
στην Ελλάδα. Δημιουργήθηκε για να βοηθήσει τους εργαζόμενους να βρουν εύκολα το
σωματείο που τους αντιπροσωπεύει, με αφορμή το 1ο φεστιβάλ του 904 Αριστερά στα
FM.

## 🌟 Χαρακτηριστικά

- **🔍 Έξυπνη Αναζήτηση**: Fuzzy search με suggestions σε πραγματικό χρόνο
- **🏷️ Φιλτράρισμα**: Ανά κλάδο (Υγεία, Εκπαίδευση, κλπ.) και τύπο σωματείου
- **📱 Responsive Design**: Λειτουργεί άψογα σε όλες τις συσκευές
- **🔗 Permalinks**: Κάθε σωματείο έχει μοναδικό URL για εύκολη κοινοποίηση
- **📞 Άμεση Επικοινωνία**: Clickable τηλέφωνα και emails
- **⚡ Γρήγορη Φόρτωση**: Single-page application χωρίς εξωτερικές εξαρτήσεις

## 🚀 Live Demo

Δείτε το site εδώ: [https://unionexpress.github.io/union-finder](https://unionexpress.github.io/union-finder)

## 🛠️ Τεχνολογίες

- **Frontend**: Vanilla HTML/CSS/JavaScript
- **Backend**: [Babashka](https://babashka.org/) (Clojure scripting)
- **Data Source**: Google Sheets API
- **Deployment**: GitHub Pages + GitHub Actions
- **Authentication**: Google Service Account (JWT)

## 📊 Δεδομένα

Τα δεδομένα των σωματείων προέρχονται από Google Sheets και περιλαμβάνουν:

- **Βασικές Πληροφορίες**: Όνομα, τύπος, κλάδος
- **Επικοινωνία**: Τηλέφωνα, emails, ιστοσελίδες
- **Social Media**: Instagram, Facebook
- **Φόρμες Εγγραφής**: Direct links για εγγραφή μελών

### Υποστηριζόμενοι Κλάδοι

- 🏥 Υγεία
- 🎓 Εκπαίδευση
- 💼 Λογιστικά
- 🔬 Έρευνα
- 🏨 Τουρισμός
- 🏗️ Κατασκευές
- 💻 ΤΠΕ (Τηλεπικοινωνίες - Πληροφορική - Έρευνα)
- 🛒 Εμπόριο
- 🏭 Βιομηχανία
- 🍕 Τρόφιμα
- 🚛 Μεταφορές
- 📋 Διοίκηση
- 🏛️ Δημόσιος Τομέας
- ⚖️ Εργασιακά
- 👥 Κοινωνικά

... και σιγά σιγά θα μπουν κι άλλοι

## 🔧 Setup & Development

### Προαπαιτούμενα

- [Babashka](https://github.com/babashka/babashka#installation)
- Google Cloud account με ενεργοποιημένο Sheets API
- Google Service Account JSON key

### Τοπική Εγκατάσταση

1. **Clone το repository:**
```bash
git clone https://github.com/unionexpress/union-finder.git
cd union-finder
```

2. **Setup Google Service Account:**
   - Δημιούργησε Service Account στο Google Cloud Console
   - Download το JSON credentials file ως `credentials.json`
   - Μοιράσου το Google Sheet με το service account email

3. **Generate το site:**
```bash
./generate_site.clj credentials.json "SPREADSHEET_ID" "SHEET_NAME" index.html
```

4. **Άνοιξε το `index.html` στο browser σου**

### Google Sheets Format

Το spreadsheet πρέπει να έχει τις εξής στήλες:

| Στήλη                       | Περιγραφή                     |
|-----------------------------|-------------------------------|
| `Όνομα σωματείου`           | Σύντομο όνομα                 |
| `Πλήρες όνομα`              | Πλήρης επωνυμία               |
| `Τύπος σωματείου`           | Κλαδικό/Επιχειρησιακό         |
| `Κλαδικό σωματείο`          | Μητρικό σωματείο (αν υπάρχει) |
| `Ιστοσελίδα σωματείου`      | Official website              |
| `Περιγραφή`                 | Σύντομη περιγραφή             |
| `Φόρμα εγγραφής`            | URL για εγγραφή               |
| `Τηλέφωνα`                  | Comma-separated               |
| `Εmail`                     | Comma-separated               |
| `Instagram`                 | Instagram URL                 |
| `Άλλοι τρόποι επικοινωνίας` | Επιπλέον contact info         |

Μελλοντικά μπορεί να προστεθούν κι άλλα πεδία

## 🤖 GitHub Actions

Το site ανανεώνεται αυτόματα:

- **Κάθε μέρα στις 09:00** (ελληνική ώρα)
- **Όταν γίνει push** στο main branch
- **Manual trigger** από το Actions tab

### Required Secrets

Στο GitHub repo → Settings → Secrets and variables → Actions:

```
GOOGLE_PROJECT_ID=your-project-id
GOOGLE_PRIVATE_KEY_ID=key-id-from-json
GOOGLE_PRIVATE_KEY=-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n
GOOGLE_CLIENT_EMAIL=service-account@project.iam.gserviceaccount.com
GOOGLE_CLIENT_ID=client-id-number
GOOGLE_CLIENT_CERT_URL=cert-url-from-json
SPREADSHEET_ID=1ABC...XYZ
```

Αυτή η πληροφορία χρειάζεται γιατί το script παίρνει τις πληροφορίες για τα
σωματεία μέσω του Google Sheets API. Αυτή τη στιγμή τα github actions
χρησιμοποιούν ένα service account που έφτιαξα για τις ανάγκες του project με
πρόσβαση στο Google Sheets API και ΜΟΝΟ στο spreadsheet με τα σωματεία και τίποτα άλλο.

Για να μπορέσετε επομένως να χρησιμοποιήσετε το script, πρεπει είτε να φτιάξετε
δικό σας spreadsheet είτε να μου ζητήσετε να σας δώσω πρόσβαση (είτε στο service
account, είτε στο αρχείο - προτιμότερο το 2ο σενάριο)

## 📁 Δομή Project

```
union-finder/
├── .github/
│   └── workflows/
│       └── generate.yml      # GitHub Actions workflow
├── files/
│   └── qrcode.svg           # QR code για το site
├── generate_site.clj        # Κύριο script generation
├── index.html              # Generated static site
└── README.md               # Αυτό το αρχείο
```

## 🎨 Customization

### Styling
Το CSS είναι inline στο HTML template. Μπορείς να τροποποιήσεις:
- Χρώματα στο `body` gradient
- Font families στο `.container`
- Card styling στο `.union-card`

### Search Algorithm
Το fuzzy search scoring βρίσκεται στη `fuzzySearch()` function. Τα scores
μπορούν να προσαρμοστούν για καλύτερα αποτελέσματα.

### Sector Classification
Η κατηγοριοποίηση γίνεται με regex patterns στη συνάρτηση `process-union-entry`.

## 🤝 Πώς μπορώ να συνεισφέρω;

1. **Fork** το repository
2. **Create feature branch**: `git checkout -b feature/amazing-feature`
3. **Commit changes**: `git commit -m 'Add amazing feature'`
4. **Push to branch**: `git push origin feature/amazing-feature`
5. **Open Pull Request**

Αν σε ξέρω ζήτα μου να σε κάνω collaborator.

### Ιδέες για έξτρα features

- [ ] Πιο προχωρημένα φίλτρα (μέγεθος, έτος ίδρυσης)
- [ ] Επιλογή για subscribe στα νέα σωματείων
- [ ] Χάρτης με τοποθεσίες σωματείων
- [ ] Ημερολόγιο εκδηλώσεων
- [ ] Rating/review system
- [ ] Υποστήριξη για επιπλέον γλώσσες πέρα των ελληνικών
- [ ] Mobile app (PWA)
- [ ] Σκοτεινό mode / themes
- [ ] Διαχωρισμός stylesheet από λογική - έστω το template της σελίδας να μην
      είναι μαζί με τον κώδικα
      
Το πιο πιθανό είναι να μην κάνω τίποτα από τα παραπάνω, αλλά αν θέλεις κάτι πολύ
ζήτα και κάτι θα κάνουμε. Μάλλον.

## 📄 Άδεια Χρήσης

Αυτό το project είναι open source και διαθέσιμο υπό την [άδεια GPLv3](LICENSE).

## 📞 Υποστήριξη

- **Issues**: Χρησιμοποίησε το [GitHub Issues](https://github.com/unionexpress/union-finder/issues)

Αν με ξέρετε ρωτήστε ελεύθερα και προσωπικά

## 🙏 Acknowledgments

- **Babashka Community** για το εκπληκτικό tool
- **Google Sheets** για το εύκολο API
- **GitHub Pages** για τη δωρεάν φιλοξενία της σελίδας μας
- **Claude** για τη βοήθεια με το prototype και το troubleshooting στη συνέχεια
- **Όλα τα σωματεία** που συμμετέχουν στον κατάλογο

---

**Made with ❤️ for the Greek labor movement**

**Αφιερωμένο με αγάπη στην ελληνική εργατική τάξη**

![QR Code](files/qrcode.svg)
