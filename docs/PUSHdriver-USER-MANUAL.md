# PUSHdriver — Manuel d’utilisation
## Ableton Push 1 avec Bitwig Studio

**Branche décrite :** `custom`  
**Base logicielle :** DrivenByMoss 19.2.2 · Bitwig Extension API 17 · Java 17  
**Matériel visé :** Ableton Push 1 d’origine, en USB-MIDI.

Ce manuel décrit les fonctions présentes dans le code de la branche `custom`. PUSHdriver reprend les modes, vues, commandes et mappings de DrivenByMoss, avec des adaptations spécifiques au Push 1 et à Bitwig. Une fonction héritée du framework n’est pas nécessairement une nouveauté de PUSHdriver.

> **Périmètre :** ce document décrit `custom`, pas les changements encore proposés dans la PR #16. Sur `custom`, In/Out peut encore sélectionner des appareils dans le mode Device. La PR #16 propose de réserver cette sélection aux flèches ; elle n’est pas incluse dans le build de référence décrit ici.

---

## 1. Installation et démarrage

### Compatibilité
- Ableton Push 1. Le Push 2 et son écran USB/HID ne sont pas pris en charge par ce paquet.
- Bitwig Studio 4.3.10 ou version compatible avec l’Extension API 17.
- Extension compilée avec Java 17.
- Ports MIDI Push 1 accessibles à Bitwig.

### Installer
1. Récupérer le fichier `PUSHdriver.bwextension` fourni avec le build.
2. Copier le fichier dans le dossier **Extensions** de Bitwig Studio (créer ce dossier s’il n’existe pas).
3. Redémarrer Bitwig Studio ou actualiser la liste des extensions si la version de Bitwig le permet.
4. Dans les paramètres de contrôleurs de Bitwig, ajouter PUSHdriver / Ableton Push et choisir les ports MIDI d’entrée et de sortie correspondant au Push 1.
5. Vérifier les réactions des pads, boutons, LED et LCD.

Le nom du port MIDI dépend du système et du pilote. Le simulateur logiciel du dépôt ne crée pas de port MIDI système et ne peut pas être choisi comme contrôleur dans une autre application.

### Première vérification
- Appuyer sur un bouton : le retour LED doit suivre l’état du contrôleur.
- Tourner un encodeur dans un mode qui l’utilise : une valeur doit évoluer.
- Vérifier l’affichage LCD.
- En cas d’absence de détection, vérifier ports MIDI, câble USB, permissions MIDI et journal Bitwig.

---

## 2. Repères physiques

| Élément | Fonction générale |
|---|---|
| Grille 8 × 8 | 64 pads sensibles à la vélocité et à la pression ; fonction déterminée par la vue active |
| Rangée supérieure (8 boutons) | Actions contextuelles du mode Device/Chains (On, Parameters, Expanded, Chains, Banks, Pin, Window, Up) ; navigation vers l’élément précédent dans Browser |
| Rangée inférieure (8 boutons) | Sélection des éléments affichés dans le menu inférieur de Device/Chains (appareils, pages de paramètres, chaînes) ; navigation vers l’élément suivant dans Browser |
| 9 encodeurs principaux | Réglage des paramètres de la vue ou du mode actif |
| 2 petits encodeurs | Fonctions contextuelles héritées du contrôleur Push |
| Touches des encodeurs | Sélection de colonne, contrôle tactile ou geste contextuel selon le mode |
| Quatre flèches | Navigation horizontale et verticale |
| Shift | Modifie plusieurs gestes, notamment la navigation par pages |
| In / Out | Navigation contextuelle Device / Browser et pages de paramètres |
| Touch Strip | Ruban tactile et pitch bend selon la vue/le mode |
| LCD | Quatre lignes de 68 caractères logiques |
| Transport | Lecture/arrêt, enregistrement et métronome selon le contexte |
| Footswitch 1 et 2 | Entrées pédales ; Footswitch 2 utilise par défaut l’action Sustain Pedal dans la configuration testée |

Les boutons sont momentanés : appui = valeur MIDI 127, relâchement = 0. Les encodeurs transmettent des déplacements relatifs. Le ruban utilise le pitch bend et un événement tactile.

---

## 3. Navigation générale

### Flèches
- **← / → sans Shift** : élément précédent/suivant dans le mode actif (piste, appareil, élément de liste ou page de séquence selon le contexte).
- **Shift + ← / →** : page précédente/suivante d’éléments dans le mode actif.
- **↑ / ↓ sans Shift** : scène précédente/suivante.
- **Shift + ↑ / ↓** : page précédente/suivante de scènes.
- Dans **Scene Play**, la banque de scènes est de 64 scènes ; Shift change de banque de 64.
- Les flèches naviguent ; elles ne lancent pas de clip ou de scène.

Les LED des flèches indiquent si le déplacement demandé est disponible. Avec Shift, elles reflètent la disponibilité d’une page entière.

### In / Out sur `custom`
- **In** : progression vers l’élément, la colonne ou la page suivante.
- **Out** : retour vers l’élément, la colonne ou la page précédente.
- En **Device**, In/Out sélectionne l’appareil précédent/suivant lorsque la liste d’appareils est affichée, et change de page de paramètres lorsque les paramètres sont affichés.
- En **Browser**, In/Out parcourt les colonnes de filtres.
- En **Device Chains**, In/Out reste inactif : l’API 17 ne fournit pas de curseur de chaîne sélectionnée suffisamment fiable pour une navigation précédente/suivante sûre.
- En **Session**, les commandes de pages de banque de pistes conservent leur comportement établi.
- Dans les vues **Sequencer**, les commandes sont transmises à la navigation de page de la vue.

> Le comportement In/Out ci-dessus est celui de `custom`. Il ne préjuge pas du résultat de la PR #16, qui n’est pas intégrée à cette branche de référence.

---

## 4. Session et gestion des clips

La vue Session présente les pistes et emplacements de clips sous forme de grille.

### Clips
- Un pad correspondant à un emplacement lance le clip de cet emplacement.
- **Duplicate + pad** : copie le clip source vers l’emplacement cible.
- **Delete + pad** : supprime le clip ciblé.
- **Stop + pad** : arrête la lecture de la piste correspondante.
- **Shift + Stop** : arrête tous les clips.
- Les banques Session sont organisées en groupes de pistes et de scènes ; la navigation par pages permet de parcourir les banques.

La couverture runtime vérifie une banque virtuelle de huit pistes × huit emplacements et une banque de 64 scènes. Le lancement réel, la quantification, les transitions et le moteur audio restent ceux de Bitwig Studio.

### Scènes
- Les flèches verticales parcourent les scènes.
- Shift + flèche verticale change de page de scènes.
- Scene Play conserve sa banque de 64 scènes.
- Les flèches servent à naviguer et ne déclenchent pas une scène par accident.

---

## 5. Pistes, mixage et transport

### Contrôle des pistes
Le modèle testé expose huit pistes indépendantes. Selon le mode actif, les encodeurs principaux pilotent notamment :
- **Volume** : niveau de chaque piste.
- **Pan** : panoramique de chaque piste.
- **Send 1** : premier départ auxiliaire de chaque piste.
- **Mute** : état muet de la piste via la commande dédiée.

Le runtime virtuel vérifie les huit encodeurs de piste, les déplacements relatifs positifs/négatifs, le toucher d’encodeur et la remise à zéro d’un paramètre par **Delete + toucher de l’encodeur** dans les scénarios couverts.

### Transport
Les commandes héritées du Push pilotent lecture/arrêt et enregistrement selon les affectations du contrôleur. Le métronome peut être activé/désactivé ; **Shift + Metronome** bascule le clic/tick dans le scénario couvert par les tests.

Le comportement précis dépend de l’état du transport et des fonctions exposées par Bitwig.

### Pédales
- Footswitch 2 (CC 69) est associé par défaut à Sustain Pedal et transmet l’appui/le relâchement correspondants.
- Footswitch 1 (CC 64) reste distinct et n’est pas lié à cette action par défaut dans la configuration testée.
- Les affectations peuvent dépendre de la configuration Bitwig et du profil de contrôleur chargé.

---

## 6. Mode Device : appareils et paramètres

Le mode Device permet de parcourir les appareils de la piste ou du contexte sélectionné, puis de modifier leurs paramètres.

### Liste d’appareils
- Les appareils disponibles sont affichés dans le menu inférieur, aligné avec la rangée physique de boutons inférieure.
- Le bouton inférieur correspondant sélectionne l’appareil affiché à sa verticale.
- La rangée supérieure conserve les commandes contextuelles de l’appareil (On, Parameters, Expanded, Chains, Banks, Pin, Window et Up).
- **In / Out** et les flèches horizontales naviguent entre les appareils sur `custom`.
- **Shift + ← / →** change de page d’appareils.
- Un appareil absent ou un emplacement vide est signalé comme indisponible.

### Pages de paramètres
- Les noms de pages sont affichés dans le menu inférieur ; le bouton inférieur correspondant sélectionne la page affichée à sa verticale.
- La rangée supérieure reste dédiée aux commandes contextuelles de l’appareil.
- **In / Out** passe à la page précédente/suivante.
- Les encodeurs principaux règlent les paramètres affichés.
- Les pages vides ne sont pas présentées comme sélectionnables.

### Rangée supérieure : commandes d’appareil
Selon les capacités exposées par Bitwig et l’appareil sélectionné, les huit boutons inférieurs correspondent à :
1. **On** : activer/désactiver l’appareil.
2. **Parameter section** : afficher/masquer une section de paramètres si l’hôte le prend en charge.
3. **Expanded** : développer/réduire l’affichage de l’appareil.
4. **Chains** : ouvrir/fermer la vue des chaînes si les chaînes de slots sont prises en charge.
5. **Devices / Parameters** : basculer entre liste d’appareils et pages de paramètres.
6. **Pin** : épingler/désépingler l’appareil si l’hôte prend cette fonction en charge.
7. **Window** : ouvrir/fermer la fenêtre de l’appareil.
8. **Up** : remonter dans la hiérarchie (paramètres → appareils → piste, ou appareil parent selon le contexte).

Les fonctions indisponibles dans l’API ou pour l’objet courant sont désactivées et signalées par une LED grise. Ce n’est pas nécessairement un défaut du contrôleur.

### Couleurs de navigation
- **Vert** : sélection ; vif pour l’élément sélectionné, atténué pour les éléments disponibles.
- **Bleu** : navigation et contexte.
- **Ambre/orange** : état et actions.
- **Orchidée/violet** : hiérarchie, notamment Chains et Up.
- **Gris atténué** : commande indisponible ou emplacement vide.

Les couleurs sont les indices de palette natifs du Push 1, pas des valeurs RGB destinées à l’écran du Push 2.

---

## 7. Device Chains

La vue Chains sert à parcourir la hiérarchie de chaînes d’un appareil lorsque Bitwig expose cette capacité.
- Les noms des chaînes sont affichés dans le menu inférieur ; le bouton inférieur correspondant sélectionne la chaîne située à sa verticale.
- La rangée supérieure conserve les commandes d’appareil, notamment **Chains** pour ouvrir/fermer cette vue et **Up** pour remonter à l’appareil ou au niveau parent.
- Les commandes de chaînes non prises en charge par l’API 17 restent désactivées.
- In/Out n’y navigue pas : l’extension évite de déplacer une sélection qui ne peut pas être identifiée de manière fiable par l’API.

La présence et la profondeur des chaînes dépendent de l’appareil Bitwig sélectionné.

---

## 8. Browser : recherche et sélection

Le Browser utilise les rangées de boutons, les encodeurs et les flèches pour parcourir catégories, filtres et résultats.

### Colonnes de filtres
- Les colonnes visibles sont présentées sur les boutons de rangée.
- **In / Out** : colonne de filtre suivante/précédente.
- **Bouton supérieur d’une colonne** : élément précédent dans cette colonne.
- **Bouton inférieur d’une colonne** : élément suivant dans cette colonne.
- Dans Browser, ces deux rangées sont des commandes de déplacement opposées dans la même liste ; elles ne représentent pas deux éléments différents à sélectionner.
- **Toucher un encodeur** : sélectionne la colonne correspondante.
- **Tourner l’encodeur touché** : change l’élément du filtre sélectionné.
- Un seul encodeur est traité comme colonne active à la fois.
- **Delete + toucher l’encodeur** : réinitialise le filtre de cette colonne lorsqu’elle existe.

### Résultats et préécoute
- Le huitième couple de boutons de rangée est dédié à la navigation dans les résultats.
- Toucher le huitième encodeur sélectionne la colonne des résultats ; tourner cet encodeur déplace la sélection.
- L’écran indique le type de contenu, les filtres, les informations et le résultat sélectionné.
- Les limites de début/fin sont respectées ; les LED indiquent les directions disponibles.
- **Shift + ← / →** : type de contenu Browser précédent/suivant.
- **Shift + bouton supérieur de la huitième colonne** : bascule la préécoute lorsque l’hôte expose cette fonction.
- La préécoute est affichée à l’écran lorsqu’elle est prise en charge.

En quittant le Browser, le mode arrête la navigation Browser côté hôte.

---

## 9. Notes, clavier et Drum

Les vues Note et Drum sont les vues de jeu du Push. La grille transmet les notes au contexte actif ; les touches peuvent être organisées selon la gamme, l’octave et le périphérique sélectionné.

### Vue Note
- Les 64 pads jouent les notes correspondant au mapping de la vue.
- La vélocité du pad est transmise à la note.
- La pression/aftertouch est transmise tant que le pad est maintenu.
- Les changements de gamme et d’octave modifient la correspondance des pads sans modifier les notes déjà enregistrées dans un clip.

### Vue Drum
- La grille pilote les pads du périphérique Drum sélectionné.
- Les banques de pads sont parcourues par blocs ; Drum64 prend en charge une organisation 4 × 4 par bloc et la couverture virtuelle vérifie le mapping des 64 adresses de notes.
- L’affichage et les couleurs dépendent du périphérique et des informations de pads exposées par Bitwig.

Les sons et la réponse audio sont gérés par Bitwig et les plug-ins chargés.

---

## 10. Séquenceur pas à pas

Le séquenceur classique programme des notes dans un clip via la grille.
- Les rangées représentent les notes disponibles ; les colonnes représentent les pas.
- Un pad de pas ajoute ou modifie la note correspondante selon l’état du pas.
- **Shift + pad de pas** déclenche le geste de répétition du séquenceur.
- **Select + pad de pas** ouvre l’édition de note lorsque cette combinaison est disponible.
- Un appui long sur un pas permet d’inspecter/éditer la note correspondante.
- Les pages se parcourent avec les flèches horizontales ou les commandes de page de la vue.

### Suivi du playhead
Le séquenceur suit automatiquement la page du playhead pendant la lecture lorsqu’un clip de notes existe.
- Une navigation manuelle gauche/droite désactive le suivi automatique pour éviter que la page ne soit immédiatement remplacée.
- Sélectionner à nouveau la page où se trouve le playhead rétablit le suivi.
- Le geste à deux pads sur les indicateurs de page modifie la boucle et conserve le suivi automatique.
- L’absence de clip ou d’état de pas est gérée sans navigation invalide.

---

## 11. Poly Sequencer

La vue Poly Sequencer est organisée en deux zones :
- **Quatre rangées inférieures** : clavier de 32 notes, sensible à la gamme et à l’octave.
- **Quatre rangées supérieures** : grille des pas du séquenceur.

### Sélection persistante de notes
1. Jouer une note sur un pad inférieur : elle est jouée et ajoutée à la sélection de programmation.
2. Relâcher le pad : la note reste dans la sélection.
3. **Select + pad inférieur** : ajoute ou retire cette note de la sélection sans la jouer.
4. Taper un pas dans la zone supérieure : toutes les notes sélectionnées sont ajoutées à ce pas.
5. Les notes déjà présentes dans ce pas sont conservées ; un tap normal n’efface pas les autres notes.

Si aucune note n’est sélectionnée, l’écran demande d’en sélectionner dans la zone inférieure.

### Éditer un pas existant
- **Appui long sur un pas** : ouvre les notes de ce pas dans la zone inférieure.
- **Pad inférieur normal** pendant cette édition : retire la note correspondante du pas.
- **Select + pad inférieur** pendant cette édition : sélectionne la note individuelle pour modifier sa durée.
- Un autre pas sélectionné ferme l’éditeur du pas précédent.
- Les gestes Shift/Select du séquenceur de base (répétition, édition) sont conservés.

La sélection persistante sert à programmer des accords ou voicings sans maintenir plusieurs pads pendant chaque insertion.

---

## 12. LCD, LED et simulateur

### LCD Push 1
L’écran n’est pas un écran pixel couleur. C’est un ensemble de quatre modules LCD alphanumériques placés côte à côte :
- 4 lignes × 68 caractères logiques.
- 4 modules physiques par ligne, 17 caractères par module.
- 8 cellules logiques par ligne, de largeur alternée 9 et 8 caractères.
- Les caractères spéciaux Push 1 sont transmis comme codes MIDI sept bits.
- Les notifications courtes peuvent occuper le bloc de droite sans écraser les 51 caractères précédents.

Le logiciel conserve les glyphes Push 1 spécifiques. Les dimensions physiques de la dalle, son espacement et sa référence exacte ne sont pas établis par les sources du projet ; aucune dimension en millimètres n’est annoncée.

### Simulateur logiciel
Après assemblage et compilation :
```bash
python3 scripts/test_push1_extension.py --gui
```
Le simulateur expose les 64 pads, vélocité, aftertouch, boutons, Shift, 11 encodeurs et leur toucher, ruban, LED, quatre modules LCD et moniteur MIDI. Il vérifie le comportement logiciel sans matériel physique. Ce n’est pas un port MIDI virtuel du système et il ne se connecte pas à une instance Bitwig séparée.

---

## 13. Dépannage rapide

| Symptôme | Vérification |
|---|---|
| Push 1 non détecté | Câble USB, ports MIDI entrée/sortie dans Bitwig, permissions et journal Bitwig |
| Pads reçus mais aucun retour LED | Port MIDI de sortie et sélection de PUSHdriver |
| LCD vide ou incohérent | Port de sortie et compatibilité Push 1 ; Push 2 non pris en charge |
| Encodeurs sans effet | Mode actif, paramètre affiché et présence d’un appareil/piste sélectionné |
| Bouton grisé | Fonction non exposée par API 17 ou indisponible pour l’objet courant |
| Browser sans résultat | Contenu, filtres et disponibilité du Browser Bitwig |
| Séquenceur qui ne suit plus | Une navigation manuelle peut avoir verrouillé la page ; sélectionner la page du playhead restaure le suivi |
| In/Out inattendu | Vérifier le contexte : Device, paramètres, Browser, Chains, Session ou Sequencer |

---

## 14. Limites et validation

Le build et les tests virtuels prouvent la compilation, la composition du paquet, les valeurs du protocole MIDI couvertes et les scénarios exécutés avec les doubles API 17. Ils ne certifient pas à eux seuls :
- le fonctionnement dans un processus Bitwig Studio 4.3.10 réel ;
- la découverte USB-MIDI sur chaque système ;
- la calibration analogique de vélocité/pression ;
- les délais électriques/USB ou les rebonds des boutons ;
- la luminosité réelle des LED et l’apparence de la vitre LCD ;
- toutes les interactions avec chaque plug-in et chaque projet.

Ces éléments nécessitent une qualification sur un Push 1 physique et dans Bitwig. Le banc virtuel sert à la régression logicielle courante, pas à remplacer les essais matériels.

---

## 15. Références
- [Ableton — Using Push 1](https://www.ableton.com/en/manual/using-push-1/)
- [Push 1 MIDI reference](https://chor.ist/push/midi.html)
- [PUSHdriver — profil matériel et protocole](PUSH1-HARDWARE.md)
- [PUSHdriver — comportement caractérisé](PUSH1-BEHAVIOR.md)
- [PUSHdriver — matrice de couverture virtuelle](VIRTUAL-PUSH1-COVERAGE.md)
- [PUSHdriver — gate de qualité et build local](DEVELOPMENT-QUALITY-GATE.md)
- [Sources DrivenByMoss 19.2.2](../UPSTREAM.md)
