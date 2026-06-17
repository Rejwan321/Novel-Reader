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

    // Initialize search filter UI states from hidden inputs on page load
    var initGenre = $("#search-filter-genre-val").val() || "ALL";
    var initYear = $("#search-filter-year-val").val() || "ALL";
    var initSort = $("#search-filter-sort-val").val() || "POPULARITY";
    var initStatus = $("#search-filter-status-val").val() || "ALL";
    var initTags = $("#search-filter-tags-val").val() || "ALL";
    var initCountry = $("#search-filter-country-val").val() || "ALL";
    var initSource = $("#search-filter-source-val").val() || "ALL";

    function selectCustomOption(groupId, value) {
        var group = $(".custom-select-group[data-id='" + groupId + "']");
        var option = group.find(".custom-option[data-value='" + value + "']");
        if (option.length === 0) {
            option = group.find(".custom-option").first();
        }
        if (option.length > 0) {
            group.find(".custom-option").removeClass("active");
            option.addClass("active");
            group.find(".selected-text").text(option.text());
            $("#" + groupId + "-val").val(value);
        }
    }

    selectCustomOption("search-filter-genre", initGenre);
    selectCustomOption("search-filter-year", initYear);
    selectCustomOption("search-filter-sort", initSort);
    selectCustomOption("search-filter-status", initStatus);
    selectCustomOption("search-filter-tags", initTags);
    selectCustomOption("search-filter-country", initCountry);
    selectCustomOption("search-filter-source", initSource);

    // Expand search bar on page load if search or any filters are active
    var currentSearchVal = $(".search-bar .input").val();
    var hasActiveFilters = (initGenre !== "ALL" || initYear !== "ALL" || initSort !== "POPULARITY" || initStatus !== "ALL" || initTags !== "ALL" || initCountry !== "ALL" || initSource !== "ALL");
    if ((currentSearchVal && currentSearchVal.trim() !== "") || hasActiveFilters) {
        $(".search-bar, .search-bar .input").addClass("active");
        $("#search-filter-icon-btn").show();
    }

    // --- Search bar behavior ---
    $(".fa-search").click(function() {
        var input = $(".search-bar .input");
        if (input.hasClass("active")) {
            $("#search-form").submit();
        } else {
            $(".search-bar, .search-bar .input").toggleClass("active");
            if ($(".search-bar").hasClass("active")) {
                input.focus();
                $("#search-filter-icon-btn").show();
            } else {
                $("#search-filter-icon-btn").hide();
                $("#search-filter-panel").addClass("d-none");
            }
        }
    });

    // Toggle search filter overlay panel
    $("#search-filter-icon-btn").click(function(e) {
        e.stopPropagation();
        $("#search-filter-panel").toggleClass("d-none");
        if (!$("#search-filter-panel").hasClass("d-none")) {
            $("#search-results-dropdown").addClass("d-none");
        }
    });

    // Stop propagation of clicks inside the filter panel
    $("#search-filter-panel").click(function(e) {
        e.stopPropagation();
    });

    // Toggle custom select dropdown menu
    $(document).on("click", ".custom-select-trigger", function(e) {
        e.stopPropagation();
        var container = $(this).closest(".custom-select-container");
        var menu = container.find(".custom-options-menu");
        
        // Close other custom options menus
        $(".custom-options-menu").not(menu).addClass("d-none");
        menu.toggleClass("d-none");
    });

    // Select custom option
    $(document).on("click", ".custom-option", function(e) {
        e.stopPropagation();
        var option = $(this);
        var val = option.data("value");
        var group = option.closest(".custom-select-group");
        var groupId = group.data("id");
        
        group.find(".custom-option").removeClass("active");
        option.addClass("active");
        group.find(".selected-text").text(option.text());
        group.find(".custom-options-menu").addClass("d-none");
        
        // Update hidden parameter input field
        $("#" + groupId + "-val").val(val);
        
        triggerSearchQuery();
    });

    function triggerSearchQuery() {
        var query = $(".search-bar .input").val().trim();
        if (query.length >= 1) {
            $(".search-bar .input").trigger("input");
        }
    }

    // Retract search bar, dropdown and filter panel if clicked outside
    $(document).click(function(e) {
        $(".custom-options-menu").addClass("d-none");
        var searchBar = $(".search-bar");
        var dropdown = $("#search-results-dropdown");
        var filterPanel = $("#search-filter-panel");
        if (!searchBar.is(e.target) && searchBar.has(e.target).length === 0 &&
            !dropdown.is(e.target) && dropdown.has(e.target).length === 0 &&
            !filterPanel.is(e.target) && filterPanel.has(e.target).length === 0) {
            
            dropdown.addClass("d-none").empty();
            filterPanel.addClass("d-none");
            if (searchBar.hasClass("active")) {
                searchBar.removeClass("active");
                var input = searchBar.find(".input");
                input.removeClass("active");
                $("#search-filter-icon-btn").hide();
                if (input.val().trim().length > 0) {
                    input.val("");
                }
            }
        }
    });

    // Real-time search dropdown suggestions on typing 1+ characters
    var searchTimeout = null;
    $(".search-bar .input").on("input", function() {
        var query = $(this).val().trim();
        var dropdown = $("#search-results-dropdown");
        
        if (searchTimeout) {
            clearTimeout(searchTimeout);
        }
        
        if (query.length >= 1) {
            searchTimeout = setTimeout(function() {
                var searchGenre = $("#search-filter-genre-val").val() || "ALL";
                var searchYear = $("#search-filter-year-val").val() || "ALL";
                var searchSort = $("#search-filter-sort-val").val() || "POPULARITY";
                var searchStatus = $("#search-filter-status-val").val() || "ALL";
                var searchTags = $("#search-filter-tags-val").val() || "ALL";
                var searchCountry = $("#search-filter-country-val").val() || "ALL";
                var searchSource = $("#search-filter-source-val").val() || "ALL";
                $.getJSON("/api/novels", { 
                    search: query,
                    genre: searchGenre,
                    year: searchYear,
                    sort: searchSort,
                    status: searchStatus,
                    tags: searchTags,
                    country: searchCountry,
                    source: searchSource
                }, function(data) {
                    dropdown.empty();
                    
                    if (!data || data.length === 0) {
                        dropdown.html('<div class="p-3 text-muted text-center font-size-sm">No results found</div>');
                        dropdown.removeClass("d-none");
                        return;
                    }
                    
                    var matchingAuthors = {};
                    var matchingGenres = {};
                    var qLower = query.toLowerCase();
                    
                    // Group results by type (only if TITLE matches)
                    var groups = {
                        "NOVEL": [],
                        "COMIC": [],
                        "MANHWA": [],
                        "MANGA": []
                    };
                    
                    data.forEach(function(item) {
                        var title = (item.title || "").toLowerCase();
                        var author = (item.author || "");
                        var genreStr = (item.genre || "");
                        
                        // 1. Author matching
                        if (author.toLowerCase().indexOf(qLower) !== -1) {
                            matchingAuthors[author] = (matchingAuthors[author] || 0) + 1;
                        }
                        
                        // 2. Genre matching
                        var genres = genreStr.split(",").map(function(g) { return g.trim(); });
                        genres.forEach(function(g) {
                            if (g.toLowerCase().indexOf(qLower) !== -1) {
                                matchingGenres[g] = (matchingGenres[g] || 0) + 1;
                            }
                        });
                        
                        // 3. Title matching (for grouped thumbnail list)
                        if (title.indexOf(qLower) !== -1) {
                            var type = (item.type || "").toUpperCase();
                            if (groups[type]) {
                                groups[type].push(item);
                            } else {
                                if (!groups["OTHER"]) groups["OTHER"] = [];
                                groups["OTHER"].push(item);
                            }
                        }
                    });
                    
                    var html = "";
                    
                    // A. Search Action Header
                    html += '<a href="#" class="search-action-item" id="search-action-submit">' +
                            '  <span class="action-text"><i class="fa-solid fa-arrow-turn-down fa-rotate-90 me-2"></i>Search for <strong>' + query + '</strong></span>' +
                            '  <i class="fa fa-arrow-right item-arrow"></i>' +
                            '</a>';
                            
                    // B. Author Rows
                    Object.keys(matchingAuthors).forEach(function(author) {
                        var count = matchingAuthors[author];
                        html += '<a href="/?search=' + encodeURIComponent(author) + '" class="search-badge-item">' +
                                '  <div class="badge-content">' +
                                '    <span class="search-badge badge-author">Author</span>' +
                                '    <span class="item-name">' + author + '</span>' +
                                '    <span class="item-count">' + count + '</span>' +
                                '  </div>' +
                                '  <i class="fa fa-arrow-right item-arrow"></i>' +
                                '</a>';
                    });
                    
                    // C. Genre Rows
                    Object.keys(matchingGenres).forEach(function(genre) {
                        var count = matchingGenres[genre];
                        html += '<a href="/?genre=' + encodeURIComponent(genre) + '" class="search-badge-item">' +
                                '  <div class="badge-content">' +
                                '    <span class="search-badge badge-genre">Genre</span>' +
                                '    <span class="item-name">' + genre + '</span>' +
                                '    <span class="item-count">' + count + '</span>' +
                                '  </div>' +
                                '  <i class="fa fa-arrow-right item-arrow"></i>' +
                                '</a>';
                    });
                    
                    // D. Grouped Series (only if titles match query)
                    var typeLabels = {
                        "NOVEL": "Novels",
                        "COMIC": "Comics",
                        "MANHWA": "Manhwa",
                        "MANGA": "Manga",
                        "OTHER": "Other"
                    };
                    
                    var hasItems = false;
                    Object.keys(groups).forEach(function(key) {
                        var list = groups[key];
                        if (list && list.length > 0) {
                            hasItems = true;
                            var label = typeLabels[key] || key;
                            html += '<div class="search-group-header">' + label + '</div>';
                            list.slice(0, 5).forEach(function(item) {
                                var ratingText = item.rating ? ' (★ ' + parseFloat(item.rating).toFixed(1) + ')' : '';
                                var meta = (item.status || "ONGOING") + ratingText;
                                html += '<a href="/novel/' + item.id + '" class="search-result-item">' +
                                        '  <img src="' + (item.coverUrl || '/uploads/default-cover.jpg') + '" class="search-result-img" alt="">' +
                                        '  <div class="search-result-info">' +
                                        '    <div class="search-result-title">' + item.title + '</div>' +
                                        '    <div class="search-result-meta">' + meta + '</div>' +
                                        '  </div>' +
                                        '</a>';
                            });
                        }
                    });
                    
                    // If no authors, genres, or titles match
                    if (Object.keys(matchingAuthors).length === 0 && Object.keys(matchingGenres).length === 0 && !hasItems) {
                        dropdown.html('<div class="p-3 text-muted text-center font-size-sm">No results found</div>');
                        dropdown.removeClass("d-none");
                        return;
                    }
                    
                    // Footer link
                    html += '<a href="#" class="search-results-footer" id="btn-view-all-results">View all results for <strong>' + query + '</strong></a>';
                    
                    dropdown.html(html);
                    dropdown.removeClass("d-none");
                    
                    // Hook search-action-submit click to submit form
                    $("#search-action-submit").click(function(e) {
                        e.preventDefault();
                        $("#search-form").submit();
                    });
                    
                    // Hook view-all click to submit form
                    $("#btn-view-all-results").click(function(e) {
                        e.preventDefault();
                        $("#search-form").submit();
                    });
                });
            }, 250);
        } else {
            dropdown.addClass("d-none").empty();
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
        var genreParam = urlParams.get("genre");
        if (genreParam) {
            selectedGenre = genreParam;
            $(".genre-chip").removeClass("active");
            $(".genre-chip").each(function() {
                if ($(this).data("genre").toLowerCase() === genreParam.toLowerCase()) {
                    $(this).addClass("active");
                }
            });
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
        var currentSize = parseInt($("#reader-content-text-body").css("font-size")) || 18;
        if (currentSize < 32) {
            var newSize = currentSize + 2;
            $("#reader-content-text-body").css("font-size", newSize + "px");
            $("#lbl-font-size").text(newSize + "px");
            localStorage.setItem("reader-size", newSize);
        }
    });

    $("#btn-font-decrease").click(function() {
        var currentSize = parseInt($("#reader-content-text-body").css("font-size")) || 18;
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

    // Autocomplete for Genres in Admin panel
    const ALL_GENRES = [
        "Action", "Adventure", "Apocalypse", "Beast World", "BL (Boys Love)", 
        "GL (Girls Love)", "Comedy", "Comic", "Cultivation", "Cyberpunk", 
        "Doujinshi", "Drama", "Ecchi", "Family", "Fantasy", "Game", 
        "Gekiga", "Gender Bender", "Guilds", "Harem", "High Fantasy", 
        "Historical", "Horror", "Isekai", "Josei", "Light Novel", 
        "LitRPG", "Magic", "Manga", "Manhua", "Manhwa", "Martial Arts", 
        "Mecha", "Military", "Modern", "Music", "Mystery", "Parody", 
        "Police", "Post-Apocalyptic", "Psychological", "Rebirth", 
        "Reincarnation", "Reverse Harem", "Romance", "Samurai", "School Life", 
        "Sci-Fi", "Seinen", "Shoujo", "Shoujo Ai", "Shounen", "Shounen Ai", 
        "Slice of Life", "Smut", "Space Opera", "Sports", "Steampunk", 
        "Super Power", "Supernatural", "Survival", "System", "Thriller", 
        "Time Travel", "Tower Climbing", "Tragedy", "Transmigration", 
        "Urban Fantasy", "Vampire", "Villainess", "Virtual Reality", 
        "Webtoon", "Werewolf", "Wuxia", "Xianxia", "Xuanhuan", "Yaoi", 
        "Yuri", "Zombie"
    ];

    const genreInput = $("#story-genre");
    const genreDropdown = $("#genre-autocomplete-dropdown");

    if (genreInput.length > 0 && genreDropdown.length > 0) {
        genreInput.on("input focus", function() {
            var val = $(this).val();
            var parts = val.split(",");
            var currentQuery = parts[parts.length - 1].trim().toLowerCase();

            if (currentQuery.length >= 1) {
                // Find already selected genres to exclude them
                var selectedGenres = parts.slice(0, parts.length - 1).map(g => g.trim().toLowerCase());
                
                var matches = ALL_GENRES.filter(function(genre) {
                    return genre.toLowerCase().includes(currentQuery) && !selectedGenres.includes(genre.toLowerCase());
                });

                if (matches.length > 0) {
                    genreDropdown.empty();
                    matches.forEach(function(genre) {
                        genreDropdown.append('<div class="genre-suggestion-item" data-value="' + genre + '">' + genre + '</div>');
                    });
                    genreDropdown.removeClass("d-none");
                } else {
                    genreDropdown.addClass("d-none");
                }
            } else {
                genreDropdown.addClass("d-none");
            }
        });

        // Click on suggestion
        genreDropdown.on("click", ".genre-suggestion-item", function() {
            var selectedVal = $(this).data("value");
            var val = genreInput.val();
            var parts = val.split(",");
            parts[parts.length - 1] = " " + selectedVal;
            
            // Clean up empty elements and format nicely
            var newVal = parts.map(p => p.trim()).filter(p => p.length > 0).join(", ") + ", ";
            genreInput.val(newVal);
            genreInput.focus();
            genreDropdown.addClass("d-none");
        });

        // Hide suggestions on clicking outside
        $(document).click(function(e) {
            if (!genreInput.is(e.target) && !genreDropdown.is(e.target) && genreDropdown.has(e.target).length === 0) {
                genreDropdown.addClass("d-none");
            }
        });
    }

});