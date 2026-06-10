// Premium Front-End Scripting - Novel Reader Platform

$(document).ready(function() {

    // --- Toast Notification Helper ---
    function showToast(message, type = "success") {
        var toastEl = document.getElementById('actionToast');
        if (!toastEl) return;
        
        var toastBody = document.getElementById('toast-message-body');
        toastBody.innerText = message;
        
        // Reset classes
        toastEl.className = "toast align-items-center text-white border-0";
        
        if (type === "success") {
            toastEl.classList.add("bg-toast-success");
        } else if (type === "error") {
            toastEl.classList.add("bg-toast-error");
        } else {
            toastEl.classList.add("bg-toast-info");
        }
        
        var toast = new bootstrap.Toast(toastEl, { delay: 3000 });
        toast.show();
    }

    // Check if redirect query asks to show login page
    var urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('showLogin')) {
        document.body.classList.add("show-popup");
        showToast("Please sign in or register to access this page.", "info");
    }

    // --- Search bar behavior ---
    $(".fa-search").click(function() {
        var input = $(".search-bar .input");
        if (input.hasClass("active") && input.val().trim().length > 0) {
            $("#search-form").submit();
        } else {
            $(".search-bar, .input").toggleClass("active");
            $(".input[type='text']").focus();
        }
    });

    // Retract search bar if clicked outside
    $(document).click(function(e) {
        var searchBar = $(".search-bar");
        if (!searchBar.is(e.target) && searchBar.has(e.target).length === 0) {
            if (searchBar.hasClass("active")) {
                searchBar.removeClass("active");
                var input = searchBar.find(".input");
                input.removeClass("active");
                if (input.val().trim().length > 0) {
                    input.val("").trigger("input");
                }
            }
        }
    });

    // Real-time search filter on typing 3+ characters
    $(".search-bar .input").on("input", function() {
        var query = $(this).val().toLowerCase().trim();
        if (query.length >= 3) {
            var cards = $(".book-card-col");
            var visibleCount = 0;
            cards.each(function() {
                var cardCol = $(this);
                var title = cardCol.find(".card-title").text().toLowerCase();
                var author = cardCol.find(".card-text").text().toLowerCase();
                if (title.includes(query) || author.includes(query)) {
                    cardCol.show();
                    visibleCount++;
                } else {
                    cardCol.hide();
                }
            });
            if (visibleCount === 0) {
                $("#empty-state-container").removeClass("d-none").show();
            } else {
                $("#empty-state-container").addClass("d-none");
            }
            $(".carousels-section, .hero-section, .recently-updated-section, .filter-section").hide();
        } else {
            $(".carousels-section, .hero-section, .recently-updated-section, .filter-section").show();
            if (typeof applyFilters === "function") {
                applyFilters();
            }
        }
    });

    // --- Sign In / Sign Up Modal Controls ---
    $(".log").click(function() {
        document.body.classList.toggle("show-popup");
    });

    $(".blur-bg-overlay").click(function() {
        document.body.classList.remove("show-popup");
    });

    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            document.body.classList.remove("show-popup");
        }
    });

    // Toggle panels in Login/Signup Modal
    $('#signup, #to-signup-btn').click(function(e) {
        e.preventDefault();
        $('#container').addClass('right-panel-active');
    });

    $('#login, #to-login-btn').click(function(e) {
        e.preventDefault();
        $('#container').removeClass('right-panel-active');
    });

    // --- AJAX Authentication Operations ---

    // Login Form Submit
    $("#login-form-modal").submit(function(e) {
        e.preventDefault();
        var email = $("#login-email").val();
        var password = $("#login-password").val();

        $.post("/api/auth/login", {
            email: email,
            password: password
        })
        .done(function(res) {
            showToast("Welcome back, " + res.user.name + "!");
            setTimeout(function() {
                location.reload();
            }, 1200);
        })
        .fail(function(err) {
            var msg = err.responseJSON && err.responseJSON.error ? err.responseJSON.error : "Invalid login credentials.";
            showToast(msg, "error");
        });
    });

    // Signup Form Submit
    $("#signup-form-modal").submit(function(e) {
        e.preventDefault();
        var name = $("#signup-name").val();
        var email = $("#signup-email").val();
        var password = $("#signup-password").val();
        var user_type = $("#signup-role").val() || "READER";

        $.post("/api/auth/signup", {
            name: name,
            email: email,
            password: password,
            user_type: user_type
        })
        .done(function(res) {
            showToast("Account created successfully! Logged in as " + res.user.name);
            setTimeout(function() {
                location.reload();
            }, 1200);
        })
        .fail(function(err) {
            var msg = err.responseJSON && err.responseJSON.error ? err.responseJSON.error : "Signup failed. Try again.";
            showToast(msg, "error");
        });
    });
    // Logout Click
    $(document).on("click", "#btn-logout", function(e) {
        e.preventDefault();
        $.post("/api/auth/logout")
        .done(function() {
            showToast("Logged out successfully.");
            setTimeout(function() {
                window.location.href = "/";
            }, 1000);
        });
    });


    // --- Bookshelf & Bookmarks Actions ---

    // Toggle Bookshelf Button (Details Page)
    $("#btn-bookshelf-toggle, #add-novel-collection-btn").click(function() {
        var btn = $(this);
        var novelId = btn.data("id");

        $.post("/api/bookmarks/toggle", { novelId: novelId })
        .done(function(res) {
            var icon = btn.find("i");
            if (res.bookmarked) {
                icon.removeClass("fa-bookmark-o").addClass("fa-bookmark text-violet");
                btn.find("span").text(res.reading ? "Reading" : "Bookmarked");
                showToast("Added to your Bookshelf.");
            } else {
                icon.removeClass("fa-bookmark text-violet").addClass("fa-bookmark-o");
                btn.find("span").text("Add Bookmark");
                showToast("Removed from your Bookshelf.");
            }
        })
        .fail(function(err) {
            if (err.status === 401) {
                // Not logged in -> trigger auth modal
                document.body.classList.add("show-popup");
                showToast("Please sign in to add this series to your bookshelf.", "info");
            } else {
                showToast("Error updating bookshelf.", "error");
            }
        });
    });

    // Remove Bookmark from Bookshelf Grid View
    $(".btn-bookshelf-remove-card").click(function() {
        var btn = $(this);
        var novelId = btn.data("id");
        var card = $("#bookmark-card-" + novelId);

        $.post("/api/bookmarks/toggle", { novelId: novelId })
        .done(function(res) {
            showToast("Removed from bookshelf.");
            card.fadeOut(400, function() {
                card.remove();
                // Check if bookshelf is now completely empty
                if ($(".bookshelf-card").length === 0) {
                    location.reload(); // Reload to trigger empty state block
                }
            });
        })
        .fail(function() {
            showToast("Error removing item.", "error");
        });
    });


    // --- Client-Side Snappy Filter Engine (Home Page) ---
    var selectedType = "ALL";
    var selectedGenre = "ALL";

    function applyFilters() {
        var cards = $(".book-card-col");
        var visibleCount = 0;

        cards.each(function() {
            var cardCol = $(this);
            var type = cardCol.data("type");
            var genresStr = cardCol.data("genres") || "";
            var genres = genresStr.split(",").map(g => g.trim().toUpperCase());

            var typeMatch = (selectedType === "ALL" || type === selectedType);
            var genreMatch = (selectedGenre === "ALL" || genres.includes(selectedGenre.toUpperCase()));

            if (typeMatch && genreMatch) {
                cardCol.fadeIn(300);
                visibleCount++;
            } else {
                cardCol.fadeOut(200);
            }
        });

        // Toggle Empty state container
        setTimeout(function() {
            if (visibleCount === 0) {
                $("#empty-state-container").removeClass("d-none").fadeIn(300);
            } else {
                $("#empty-state-container").addClass("d-none");
            }
        }, 210);
    }

    // Tab Type Selection
    $(".filter-tab").click(function() {
        $(".filter-tab").removeClass("active");
        $(this).addClass("active");
        selectedType = $(this).data("type");
        applyFilters();
    });

    // Nav-dropdown items filter trigger (from Explore menu)
    $(".genre-filter-nav").click(function(e) {
        e.preventDefault();
        var targetType = $(this).data("type");
        if (window.location.pathname !== "/") {
            // If not on home, redirect to home with query parameters
            window.location.href = "/?type=" + targetType;
            return;
        }
        // Active format tab matching selection
        $(".filter-tab").removeClass("active");
        if (targetType === "NOVEL") {
            $("#tab-novels").addClass("active");
        } else if (targetType === "COMIC") {
            $("#tab-comics").addClass("active");
        } else if (targetType === "MANHWA") {
            $("#tab-manhwa").addClass("active");
        } else if (targetType === "MANGA") {
            $("#tab-manga").addClass("active");
        } else {
            $("#tab-all").addClass("active");
        }
        selectedType = targetType;
        applyFilters();
    });

    // Check url params for homepage filtering
    if (window.location.pathname === "/") {
        var filterParam = urlParams.get("type");
        if (filterParam) {
            selectedType = filterParam.toUpperCase();
            $(".filter-tab").removeClass("active");
            if (selectedType === "NOVEL") $("#tab-novels").addClass("active");
            if (selectedType === "COMIC") $("#tab-comics").addClass("active");
            if (selectedType === "MANHWA") $("#tab-manhwa").addClass("active");
            if (selectedType === "MANGA") $("#tab-manga").addClass("active");
            applyFilters();
        }
    }

    // Genre Chip Selection
    $(".genre-chip").click(function() {
        $(".genre-chip").removeClass("active");
        $(this).addClass("active");
        selectedGenre = $(this).data("genre");
        applyFilters();
    });


    // --- Novel Reader Settings Panel ---

    // Toggle setting panel drawer
    $("#btn-reader-settings").click(function(e) {
        e.stopPropagation();
        $("#reader-settings-panel-drawer").toggleClass("d-none");
    });

    // Close settings if click outside
    $(document).click(function(e) {
        var panel = $("#reader-settings-panel-drawer");
        if (!panel.is(e.target) && panel.has(e.target).length === 0 && !$("#btn-reader-settings").is(e.target)) {
            panel.addClass("d-none");
        }
    });

    // Load persisted configurations on reader load
    if ($("#novel-reading-frame").length > 0) {
        var textBody = $("#reader-content-text-body");
        
        // Font Family
        var savedFont = localStorage.getItem("reader-font") || "Lora";
        textBody.css("font-family", "'" + savedFont + "', serif");
        $(".font-selector-btn").removeClass("active");
        $(".font-selector-btn[data-font='" + savedFont + "']").addClass("active");

        // Font Size
        var savedSize = parseInt(localStorage.getItem("reader-size")) || 18;
        textBody.css("font-size", savedSize + "px");
        $("#lbl-font-size").text(savedSize + "px");

        // Background Color Preset Theme
        var savedTheme = localStorage.getItem("reader-theme") || "dark";
        applyThemePreset(savedTheme);
    }

    // Font Family selector click
    $(".font-selector-btn").click(function() {
        var font = $(this).data("font");
        $(".font-selector-btn").removeClass("active");
        $(this).addClass("active");
        
        var fontFamilyVal = "'" + font + "', sans-serif";
        if (font === "Lora" || font === "Georgia") {
            fontFamilyVal = "'" + font + "', serif";
        } else if (font === "monospace") {
            fontFamilyVal = "monospace";
        }
        
        $("#reader-content-text-body").css("font-family", fontFamilyVal);
        localStorage.setItem("reader-font", font);
    });

    // Font Size Increase / Decrease
    $("#btn-font-increase").click(function() {
        var currentSize = parseInt($("#reader-content-text-body").css("font-size"));
        if (currentSize < 32) {
            var newSize = currentSize + 2;
            $("#reader-content-text-body").css("font-size", newSize + "px");
            $("#lbl-font-size").text(newSize + "px");
            localStorage.setItem("reader-size", newSize);
        }
    });

    $("#btn-font-decrease").click(function() {
        var currentSize = parseInt($("#reader-content-text-body").css("font-size"));
        if (currentSize > 12) {
            var newSize = currentSize - 2;
            $("#reader-content-text-body").css("font-size", newSize + "px");
            $("#lbl-font-size").text(newSize + "px");
            localStorage.setItem("reader-size", newSize);
        }
    });

    // Theme preset switcher click
    $(".theme-preset-btn").click(function() {
        var theme = $(this).data("theme");
        applyThemePreset(theme);
    });

    function applyThemePreset(theme) {
        $(".theme-preset-btn").removeClass("active");
        $(".theme-preset-btn[data-theme='" + theme + "']").addClass("active");
        
        // Reset classes
        $("body").removeClass("theme-light theme-sepia");
        
        if (theme === "light") {
            $("body").addClass("theme-light");
        } else if (theme === "sepia") {
            $("body").addClass("theme-sepia");
        } // "dark" doesn't require extra body class, uses default dark stylesheet
        
        localStorage.setItem("reader-theme", theme);
    }

    // --- Purchase Snow Flakes Event Listeners ---
    $(document).on("click", "#btn-navbar-balance", function(e) {
        e.preventDefault();
        var purchaseModal = new bootstrap.Modal(document.getElementById('purchaseFlakesModal'));
        purchaseModal.show();
    });

    $(document).on("click", ".btn-purchase-pack", function(e) {
        e.preventDefault();
        var btn = $(this);
        var amount = btn.data("amount");

        btn.prop("disabled", true).html('<i class="fa fa-spinner fa-spin"></i> Processing...');

        $.post("/api/user/purchase-flakes", { amount: amount })
        .done(function(res) {
            showToast(res.message);
            $("#navbar-user-balance").text(res.newBalance);
            
            // Close modal
            var modalEl = document.getElementById('purchaseFlakesModal');
            var modal = bootstrap.Modal.getInstance(modalEl);
            if (modal) {
                modal.hide();
            }
        })
        .fail(function(err) {
            var msg = err.responseJSON && err.responseJSON.error ? err.responseJSON.error : "Failed to purchase Snow Flakes.";
            showToast(msg, "error");
        })
        .always(function() {
            btn.prop("disabled", false).text("Purchase");
        });
    });

    // Toggle feature/highlight novel
    $(document).on("click", ".btn-toggle-feature", function(e) {
        e.preventDefault();
        var btn = $(this);
        var novelId = btn.data("id");

        btn.prop("disabled", true);

        $.post("/api/admin/novels/" + novelId + "/feature")
        .done(function(res) {
            showToast(res.message);
            setTimeout(function() {
                location.reload();
            }, 800);
        })
        .fail(function(err) {
            var msg = err.responseJSON && err.responseJSON.error ? err.responseJSON.error : "Failed to feature story.";
            showToast(msg, "error");
            btn.prop("disabled", false);
        });
    });

});