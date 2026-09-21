package com.pranav.study.cet_study_sprint

/** NCERT textbook chapter tracker for JEE and NEET study planning.
 * This is a textbook checklist, not a claim that every chapter is in an entrance exam.
 */
object SyllabusData {
    private val cetEleven = mapOf(
        "Physics" to listOf("Vectors", "Error Analysis", "Motion in a Plane", "Laws of Motion", "Gravitation", "Thermal Properties", "Sound", "Optics", "Electrostatics", "Semiconductors"),
        "Chemistry" to listOf("Basic Concepts", "Structure of Atom", "Chemical Bonding", "Redox Reactions", "States of Matter", "Hydrocarbons", "Organic Chemistry"),
        "Mathematics" to listOf("Trigonometry II", "Straight Line", "Circle", "Probability", "Complex Numbers", "Functions", "Limits", "Continuity")
    )
    private val cetTwelve = mapOf(
        "Physics" to listOf("Rotational Dynamics", "Thermodynamics", "Oscillations", "Wave Optics", "Current Electricity", "Magnetic Fields", "Electromagnetic Induction", "AC Circuits", "Semiconductor Devices"),
        "Chemistry" to listOf("Solid State", "Solutions", "Ionic Equilibria", "Electrochemistry", "Chemical Kinetics", "Coordination Compounds", "Amines", "Biomolecules"),
        "Mathematics" to listOf("Matrices", "Trigonometric Functions", "Vectors", "Line and Plane", "Differentiation", "Integration", "Differential Equations", "Probability Distributions")
    )
    fun chapters(exam: String, grade: String): Map<String, List<String>> = chapters(exam, grade, cetEleven, cetTwelve)
    private val eleven = mapOf(
        "Physics" to listOf("Units and Measurement", "Motion in a Straight Line", "Motion in a Plane", "Laws of Motion", "Work, Energy and Power", "System of Particles and Rotational Motion", "Gravitation", "Mechanical Properties of Solids", "Mechanical Properties of Fluids", "Thermal Properties of Matter", "Thermodynamics", "Kinetic Theory", "Oscillations", "Waves"),
        "Chemistry" to listOf("Some Basic Concepts of Chemistry", "Structure of Atom", "Classification of Elements and Periodicity in Properties", "Chemical Bonding and Molecular Structure", "Thermodynamics", "Equilibrium", "Redox Reactions", "Organic Chemistry: Some Basic Principles and Techniques", "Hydrocarbons"),
        "Mathematics" to listOf("Sets", "Relations and Functions", "Trigonometric Functions", "Complex Numbers and Quadratic Equations", "Linear Inequalities", "Permutations and Combinations", "Binomial Theorem", "Sequences and Series", "Straight Lines", "Conic Sections", "Introduction to Three Dimensional Geometry", "Limits and Derivatives", "Statistics", "Probability"),
        "Biology" to listOf("The Living World", "Biological Classification", "Plant Kingdom", "Animal Kingdom", "Morphology of Flowering Plants", "Anatomy of Flowering Plants", "Structural Organisation in Animals", "Cell: The Unit of Life", "Biomolecules", "Cell Cycle and Cell Division", "Photosynthesis in Higher Plants", "Respiration in Plants", "Plant Growth and Development", "Breathing and Exchange of Gases", "Body Fluids and Circulation", "Excretory Products and Their Elimination", "Locomotion and Movement", "Neural Control and Coordination", "Chemical Coordination and Integration")
    )
    private val twelve = mapOf(
        "Physics" to listOf("Electric Charges and Fields", "Electrostatic Potential and Capacitance", "Current Electricity", "Moving Charges and Magnetism", "Magnetism and Matter", "Electromagnetic Induction", "Alternating Current", "Electromagnetic Waves", "Ray Optics and Optical Instruments", "Wave Optics", "Dual Nature of Radiation and Matter", "Atoms", "Nuclei", "Semiconductor Electronics"),
        "Chemistry" to listOf("Solutions", "Electrochemistry", "Chemical Kinetics", "The d- and f-Block Elements", "Coordination Compounds", "Haloalkanes and Haloarenes", "Alcohols, Phenols and Ethers", "Aldehydes, Ketones and Carboxylic Acids", "Amines", "Biomolecules"),
        "Mathematics" to listOf("Relations and Functions", "Inverse Trigonometric Functions", "Matrices", "Determinants", "Continuity and Differentiability", "Applications of Derivatives", "Integrals", "Applications of Integrals", "Differential Equations", "Vector Algebra", "Three Dimensional Geometry", "Linear Programming", "Probability"),
        "Biology" to listOf("Sexual Reproduction in Flowering Plants", "Human Reproduction", "Reproductive Health", "Principles of Inheritance and Variation", "Molecular Basis of Inheritance", "Evolution", "Human Health and Disease", "Microbes in Human Welfare", "Biotechnology: Principles and Processes", "Biotechnology and Its Applications", "Organisms and Populations", "Ecosystem", "Biodiversity and Conservation")
    )
    fun chapters(exam: String, grade: String, cet11: Map<String, List<String>>, cet12: Map<String, List<String>>): Map<String, List<String>> {
        if (exam == "CET") return if (grade == "11") cet11 else cet12
        val all = if (grade == "11") eleven else twelve
        val subjects = if (exam == "NEET") listOf("Physics", "Chemistry", "Biology") else listOf("Physics", "Chemistry", "Mathematics")
        return subjects.associateWith { all[it].orEmpty() }
    }
}
